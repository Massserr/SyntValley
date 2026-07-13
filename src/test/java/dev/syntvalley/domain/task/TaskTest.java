package dev.syntvalley.domain.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.syntvalley.domain.identity.CitizenId;
import dev.syntvalley.domain.identity.TaskId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskTest {
    private static final TaskId TASK = new TaskId(UUID.fromString("44444444-4444-4444-4444-444444444444"));
    private static final CitizenId CITIZEN = new CitizenId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

    private static Task created() {
        return Task.create(TASK, CITIZEN, TaskKind.REST, 0);
    }

    private static Task changed(TaskTransitionResult result) {
        return assertInstanceOf(TaskTransitionResult.Changed.class, result).task();
    }

    @Test
    void runToSuccess() {
        Task task = created();
        assertTrue(task.isReady(0));

        Task running = changed(task.start(100, 0));
        assertEquals(TaskState.RUNNING, running.state());
        assertEquals(100, running.leaseExpiryGameTime());
        assertFalse(running.leaseExpired(50));
        assertTrue(running.leaseExpired(150));

        Task done = changed(running.succeed());
        assertEquals(TaskState.SUCCEEDED, done.state());
        assertTrue(done.state().isTerminal());
        assertEquals(0, done.leaseExpiryGameTime());
    }

    @Test
    void failRetriesWithBackoffThenTerminates() {
        TaskRetryPolicy policy = new TaskRetryPolicy(2, 40);
        Task running = changed(created().start(100, 0));

        Task retry = changed(running.fail(TaskFailureReason.NO_PATH, 10, policy));
        assertEquals(TaskState.PENDING, retry.state());
        assertEquals(1, retry.attempt());
        assertEquals(TaskFailureReason.NO_PATH, retry.failureReason().orElseThrow());
        assertFalse(retry.isReady(40), "backoff blocks readiness");
        assertTrue(retry.isReady(50));

        assertEquals(TaskTransitionRejection.BACKOFF_ACTIVE,
                assertInstanceOf(TaskTransitionResult.Rejected.class, retry.start(200, 40)).reason());

        Task rerun = changed(retry.start(200, 50));
        Task terminal = changed(rerun.fail(TaskFailureReason.TIMEOUT, 60, policy));
        assertEquals(TaskState.FAILED, terminal.state());
        assertEquals(2, terminal.attempt());
        assertEquals(TaskFailureReason.TIMEOUT, terminal.failureReason().orElseThrow());
    }

    @Test
    void guardsRejectInvalidTransitions() {
        assertEquals(TaskTransitionRejection.NOT_RUNNING,
                assertInstanceOf(TaskTransitionResult.Rejected.class, created().succeed()).reason());

        Task cancelled = changed(created().cancel());
        assertEquals(TaskState.CANCELLED, cancelled.state());
        assertEquals(TaskTransitionRejection.ALREADY_TERMINAL,
                assertInstanceOf(TaskTransitionResult.Rejected.class, cancelled.cancel()).reason());
        assertEquals(TaskTransitionRejection.NOT_PENDING,
                assertInstanceOf(TaskTransitionResult.Rejected.class, cancelled.start(100, 0)).reason());
    }
}
