package dev.syntvalley.ai.orchestration;

import java.util.Objects;
import java.util.UUID;

/** Immediate, typed outcome of submitting a request: queued for a worker, or rejected right away. */
public sealed interface SubmitResult {
    enum Rejection { QUEUE_FULL, CIRCUIT_OPEN, SHUTDOWN }

    record Accepted(UUID requestId) implements SubmitResult {
        public Accepted {
            Objects.requireNonNull(requestId, "requestId");
        }
    }

    record Rejected(Rejection reason) implements SubmitResult {
        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
