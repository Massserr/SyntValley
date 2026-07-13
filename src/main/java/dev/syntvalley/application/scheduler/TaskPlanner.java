package dev.syntvalley.application.scheduler;

import dev.syntvalley.domain.need.NeedKind;
import dev.syntvalley.domain.need.Needs;
import dev.syntvalley.domain.task.TaskKind;
import java.util.Objects;
import java.util.Optional;

/**
 * Deterministic, LLM-free task selection. A critical need preempts a soft IDLE task; a running
 * critical task is allowed to finish even once the need recovers; a more severe critical need
 * preempts a less severe one.
 */
public final class TaskPlanner {
    public TaskDecision plan(Optional<TaskKind> current, Needs needs) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(needs, "needs");

        TaskKind desired = desired(needs);
        if (current.isEmpty()) {
            return new TaskDecision.Start(desired);
        }

        TaskKind running = current.orElseThrow();
        if (running == desired) {
            return new TaskDecision.Keep();
        }

        boolean desiredCritical = desired != TaskKind.IDLE;
        boolean runningCritical = running != TaskKind.IDLE;
        if (desiredCritical && !runningCritical) {
            return new TaskDecision.Preempt(desired);
        }
        if (!desiredCritical) {
            return new TaskDecision.Keep();
        }
        return new TaskDecision.Preempt(desired);
    }

    public TaskKind desired(Needs needs) {
        Objects.requireNonNull(needs, "needs");
        return needs.mostCritical()
                .map(kind -> kind == NeedKind.HUNGER ? TaskKind.REQUEST_FOOD : TaskKind.REST)
                .orElse(TaskKind.IDLE);
    }
}
