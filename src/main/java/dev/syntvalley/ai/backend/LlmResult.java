package dev.syntvalley.ai.backend;

import java.util.Objects;
import java.util.UUID;

/** Outcome of one generation: a successful response or a typed failure with a bounded diagnostic. */
public sealed interface LlmResult {
    UUID requestId();

    record Success(LlmResponse response) implements LlmResult {
        public Success {
            Objects.requireNonNull(response, "response");
        }

        @Override
        public UUID requestId() {
            return response.requestId();
        }
    }

    record Failure(UUID requestId, LlmErrorKind kind, String diagnostic) implements LlmResult {
        public static final int MAX_DIAGNOSTIC_CHARS = 256;

        public Failure {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(diagnostic, "diagnostic");
            if (diagnostic.length() > MAX_DIAGNOSTIC_CHARS) {
                diagnostic = diagnostic.substring(0, MAX_DIAGNOSTIC_CHARS);
            }
        }
    }
}
