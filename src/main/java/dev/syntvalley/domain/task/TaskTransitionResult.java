package dev.syntvalley.domain.task;

import java.util.Objects;

public sealed interface TaskTransitionResult {
    record Changed(Task task) implements TaskTransitionResult {
        public Changed {
            Objects.requireNonNull(task, "task");
        }
    }

    record Rejected(TaskTransitionRejection reason) implements TaskTransitionResult {
        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
