package dev.syntvalley.ai.ollama;

import dev.syntvalley.ai.backend.LlmErrorKind;
import java.util.Objects;

/** A transport-level failure the adapter maps straight to a typed {@link LlmErrorKind}. */
public final class OllamaHttpException extends Exception {
    private final transient LlmErrorKind kind;

    public OllamaHttpException(LlmErrorKind kind, String diagnostic) {
        super(diagnostic);
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    public LlmErrorKind kind() {
        return kind;
    }
}
