package dev.syntvalley.ai.backend;

import java.util.Objects;
import java.util.UUID;

/**
 * A neutral, bounded LLM request. Carries no Minecraft objects — only an id for correlation, a short
 * purpose tag for metrics/audit, and the prompt text. Prompts are size-capped so a bug can never ship a
 * whole save file to the backend.
 */
public record LlmRequest(UUID id, String purpose, String prompt) {
    public static final int MAX_PURPOSE_CHARS = 64;
    public static final int MAX_PROMPT_CHARS = 8_192;

    public LlmRequest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(prompt, "prompt");
        if (purpose.isBlank() || purpose.length() > MAX_PURPOSE_CHARS) {
            throw new IllegalArgumentException("purpose must be 1.." + MAX_PURPOSE_CHARS + " characters");
        }
        if (prompt.isBlank() || prompt.length() > MAX_PROMPT_CHARS) {
            throw new IllegalArgumentException("prompt must be 1.." + MAX_PROMPT_CHARS + " characters");
        }
    }

    public static LlmRequest of(String purpose, String prompt) {
        return new LlmRequest(UUID.randomUUID(), purpose, prompt);
    }
}
