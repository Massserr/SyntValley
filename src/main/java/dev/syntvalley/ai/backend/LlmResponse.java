package dev.syntvalley.ai.backend;

import java.util.Objects;
import java.util.UUID;

/** A successful, size-capped LLM completion, correlated to its request by id. */
public record LlmResponse(UUID requestId, String content, long durationMillis) {
    public static final int MAX_CONTENT_CHARS = 16_384;

    public LlmResponse {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(content, "content");
        if (content.length() > MAX_CONTENT_CHARS) {
            throw new IllegalArgumentException("content exceeds " + MAX_CONTENT_CHARS + " characters");
        }
        if (durationMillis < 0) {
            throw new IllegalArgumentException("durationMillis must not be negative");
        }
    }
}
