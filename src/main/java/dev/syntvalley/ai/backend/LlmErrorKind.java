package dev.syntvalley.ai.backend;

/** Typed backend failure classes; retry policy is decided per kind by the orchestration layer. */
public enum LlmErrorKind {
    TIMEOUT,
    CONNECT_FAILED,
    HTTP_ERROR,
    MALFORMED_RESPONSE,
    RESPONSE_TOO_LARGE,
    CANCELLED
}
