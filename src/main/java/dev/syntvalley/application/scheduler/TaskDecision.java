package dev.syntvalley.application.scheduler;

import dev.syntvalley.domain.task.TaskKind;
import java.util.Objects;

public sealed interface TaskDecision {
    record Keep() implements TaskDecision {
    }

    record Start(TaskKind kind) implements TaskDecision {
        public Start {
            Objects.requireNonNull(kind, "kind");
        }
    }

    record Preempt(TaskKind kind) implements TaskDecision {
        public Preempt {
            Objects.requireNonNull(kind, "kind");
        }
    }
}
