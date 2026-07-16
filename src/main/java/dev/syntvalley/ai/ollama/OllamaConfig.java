package dev.syntvalley.ai.ollama;

import java.util.Objects;

/**
 * Connection settings for the Ollama adapter. Values arrive from server config; the record enforces the
 * safety rails (finite deadlines, bounded response size) regardless of what the config file says.
 */
public record OllamaConfig(
        String baseUrl,
        String model,
        long connectTimeoutMillis,
        long requestTimeoutMillis,
        int maxResponseChars
) {
    public OllamaConfig {
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(model, "model");
        if (baseUrl.isBlank() || !(baseUrl.startsWith("http://") || baseUrl.startsWith("https://"))) {
            throw new IllegalArgumentException("baseUrl must be an http(s) URL");
        }
        if (model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
        if (connectTimeoutMillis < 1 || requestTimeoutMillis < 1) {
            throw new IllegalArgumentException("timeouts must be positive");
        }
        if (maxResponseChars < 1) {
            throw new IllegalArgumentException("maxResponseChars must be positive");
        }
        baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** Local qwen3:8b with conservative deadlines; the production default for this project. */
    public static OllamaConfig defaults() {
        return new OllamaConfig("http://127.0.0.1:11434", "qwen3:8b", 2_000, 30_000, 65_536);
    }
}
