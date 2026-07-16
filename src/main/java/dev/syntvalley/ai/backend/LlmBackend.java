package dev.syntvalley.ai.backend;

/**
 * A replaceable LLM backend (Ollama in production, fakes in tests). {@link #generate} is a blocking
 * call and is only ever invoked on the executor's worker threads — never on the server thread. The
 * adapter owns its own connect/request deadlines and returns a typed result instead of throwing.
 */
public interface LlmBackend {
    LlmResult generate(LlmRequest request);

    default String name() {
        return getClass().getSimpleName();
    }
}
