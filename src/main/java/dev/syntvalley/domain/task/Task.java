package dev.syntvalley.domain.task;

import dev.syntvalley.domain.identity.CitizenId;
import dev.syntvalley.domain.identity.TaskId;
import java.util.Objects;
import java.util.Optional;

/**
 * Persistent semantic task. The lease binds a RUNNING task to a worker for a bounded time; after
 * unload/restart the lease is not trusted and the task is reconciled. Failures retry with backoff up
 * to a cap, after which the task is terminally FAILED.
 */
public record Task(
        TaskId id,
        CitizenId citizenId,
        TaskKind kind,
        TaskState state,
        int attempt,
        long createdGameTime,
        long leaseExpiryGameTime,
        Optional<TaskFailureReason> failureReason,
        long notBeforeGameTime
) {
    public Task {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(citizenId, "citizenId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(state, "state");
        failureReason = Objects.requireNonNull(failureReason, "failureReason");

        if (attempt < 0) {
            throw new IllegalArgumentException("attempt must not be negative");
        }
        if (createdGameTime < 0 || notBeforeGameTime < 0 || leaseExpiryGameTime < 0) {
            throw new IllegalArgumentException("game times must not be negative");
        }
        if (state == TaskState.RUNNING ? leaseExpiryGameTime <= 0 : leaseExpiryGameTime != 0) {
            throw new IllegalArgumentException("lease is set only while RUNNING");
        }
    }

    public static Task create(TaskId id, CitizenId citizenId, TaskKind kind, long gameTime) {
        return new Task(id, citizenId, kind, TaskState.PENDING, 0, gameTime, 0, Optional.empty(), gameTime);
    }

    public boolean isReady(long now) {
        return state == TaskState.PENDING && now >= notBeforeGameTime;
    }

    public boolean leaseExpired(long now) {
        return state == TaskState.RUNNING && now > leaseExpiryGameTime;
    }

    public TaskTransitionResult start(long leaseExpiry, long now) {
        if (state != TaskState.PENDING) {
            return new TaskTransitionResult.Rejected(TaskTransitionRejection.NOT_PENDING);
        }
        if (now < notBeforeGameTime) {
            return new TaskTransitionResult.Rejected(TaskTransitionRejection.BACKOFF_ACTIVE);
        }
        if (leaseExpiry <= now) {
            throw new IllegalArgumentException("lease must expire in the future");
        }
        return new TaskTransitionResult.Changed(new Task(
                id, citizenId, kind, TaskState.RUNNING, attempt, createdGameTime,
                leaseExpiry, Optional.empty(), notBeforeGameTime));
    }

    public TaskTransitionResult succeed() {
        if (state != TaskState.RUNNING) {
            return new TaskTransitionResult.Rejected(TaskTransitionRejection.NOT_RUNNING);
        }
        return new TaskTransitionResult.Changed(new Task(
                id, citizenId, kind, TaskState.SUCCEEDED, attempt, createdGameTime,
                0, Optional.empty(), notBeforeGameTime));
    }

    public TaskTransitionResult fail(TaskFailureReason reason, long now, TaskRetryPolicy policy) {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(policy, "policy");
        if (state != TaskState.RUNNING) {
            return new TaskTransitionResult.Rejected(TaskTransitionRejection.NOT_RUNNING);
        }
        int nextAttempt = attempt + 1;
        if (nextAttempt >= policy.maxAttempts()) {
            return new TaskTransitionResult.Changed(new Task(
                    id, citizenId, kind, TaskState.FAILED, nextAttempt, createdGameTime,
                    0, Optional.of(reason), notBeforeGameTime));
        }
        return new TaskTransitionResult.Changed(new Task(
                id, citizenId, kind, TaskState.PENDING, nextAttempt, createdGameTime,
                0, Optional.of(reason), now + policy.backoffTicks()));
    }

    public TaskTransitionResult cancel() {
        if (state.isTerminal()) {
            return new TaskTransitionResult.Rejected(TaskTransitionRejection.ALREADY_TERMINAL);
        }
        return new TaskTransitionResult.Changed(new Task(
                id, citizenId, kind, TaskState.CANCELLED, attempt, createdGameTime,
                0, failureReason, notBeforeGameTime));
    }
}
