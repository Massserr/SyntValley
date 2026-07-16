package dev.syntvalley.ai.ollama;

import dev.syntvalley.ai.backend.LlmBackend;
import dev.syntvalley.ai.backend.LlmErrorKind;
import dev.syntvalley.ai.backend.LlmRequest;
import dev.syntvalley.ai.backend.LlmResponse;
import dev.syntvalley.ai.backend.LlmResult;
import java.util.Objects;

/**
 * The Ollama backend over {@code POST /api/generate} with {@code stream=false}. Blocking, so it runs
 * only on the executor's worker threads. The network itself lives behind {@link OllamaHttp}; this class
 * owns the classification: it never throws, every failure becomes a typed {@link LlmResult.Failure}, and
 * diagnostics stay short and prompt-free so nothing sensitive reaches logs. JSON is built and parsed by
 * {@link OllamaEnvelope}, so the mod carries no JSON-library dependency. The response is size-capped via
 * the declared Content-Length and re-checked after reading.
 */
public final class OllamaLlmBackend implements LlmBackend {
    private final OllamaConfig config;
    private final OllamaHttp http;

    public OllamaLlmBackend(OllamaConfig config) {
        this(config, new JdkOllamaHttp(config));
    }

    public OllamaLlmBackend(OllamaConfig config, OllamaHttp http) {
        this.config = Objects.requireNonNull(config, "config");
        this.http = Objects.requireNonNull(http, "http");
    }

    @Override
    public LlmResult generate(LlmRequest request) {
        Objects.requireNonNull(request, "request");
        long startedAt = System.currentTimeMillis();

        String body = "{\"model\":" + OllamaEnvelope.quote(config.model())
                + ",\"prompt\":" + OllamaEnvelope.quote(request.prompt())
                + ",\"stream\":false}";

        OllamaHttp.Reply reply;
        try {
            reply = http.send(config.baseUrl() + "/api/generate", body);
        } catch (OllamaHttpException failure) {
            return failure(request, failure.kind(), failure.getMessage());
        }

        if (reply.declaredLength() > config.maxResponseChars()) {
            return failure(request, LlmErrorKind.RESPONSE_TOO_LARGE, "content-length " + reply.declaredLength());
        }
        if (reply.status() != 200) {
            return failure(request, LlmErrorKind.HTTP_ERROR, "status " + reply.status());
        }
        if (reply.body().length() > config.maxResponseChars()) {
            return failure(request, LlmErrorKind.RESPONSE_TOO_LARGE, "body chars " + reply.body().length());
        }

        OllamaEnvelope envelope;
        try {
            envelope = OllamaEnvelope.parse(reply.body());
        } catch (RuntimeException malformed) {
            return failure(request, LlmErrorKind.MALFORMED_RESPONSE, malformed.getClass().getSimpleName());
        }
        if (!envelope.done()) {
            return failure(request, LlmErrorKind.MALFORMED_RESPONSE, "done flag is false");
        }
        String content = envelope.response();
        if (content.length() > LlmResponse.MAX_CONTENT_CHARS) {
            return failure(request, LlmErrorKind.RESPONSE_TOO_LARGE, "response chars " + content.length());
        }
        return new LlmResult.Success(new LlmResponse(request.id(), content, System.currentTimeMillis() - startedAt));
    }

    @Override
    public String name() {
        return "ollama(" + config.model() + ")";
    }

    private static LlmResult failure(LlmRequest request, LlmErrorKind kind, String diagnostic) {
        return new LlmResult.Failure(request.id(), kind, diagnostic == null ? kind.name() : diagnostic);
    }
}
