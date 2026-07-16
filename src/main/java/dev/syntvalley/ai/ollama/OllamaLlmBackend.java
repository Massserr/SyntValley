package dev.syntvalley.ai.ollama;

import dev.syntvalley.ai.backend.LlmBackend;
import dev.syntvalley.ai.backend.LlmErrorKind;
import dev.syntvalley.ai.backend.LlmRequest;
import dev.syntvalley.ai.backend.LlmResponse;
import dev.syntvalley.ai.backend.LlmResult;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * The Ollama HTTP adapter over {@code POST /api/generate} with {@code stream=false}. Blocking, so it
 * runs only on the executor's worker threads. Every failure is classified into a typed result — the
 * adapter never throws — and diagnostics stay short and prompt-free so nothing sensitive reaches logs.
 * The response envelope is size-capped via Content-Length when present and re-checked after reading.
 * JSON is built and parsed by {@link OllamaEnvelope}, so the mod carries no JSON-library dependency.
 */
public final class OllamaLlmBackend implements LlmBackend {
    private final OllamaConfig config;
    private final HttpClient client;

    public OllamaLlmBackend(OllamaConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.connectTimeoutMillis()))
                .build();
    }

    @Override
    public LlmResult generate(LlmRequest request) {
        Objects.requireNonNull(request, "request");
        long startedAt = System.currentTimeMillis();

        String body = "{\"model\":" + OllamaEnvelope.quote(config.model())
                + ",\"prompt\":" + OllamaEnvelope.quote(request.prompt())
                + ",\"stream\":false}";

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(config.baseUrl() + "/api/generate"))
                .timeout(Duration.ofMillis(config.requestTimeoutMillis()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response;
        try {
            response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException timeout) {
            return failure(request, LlmErrorKind.TIMEOUT, "request deadline exceeded");
        } catch (ConnectException refused) {
            return failure(request, LlmErrorKind.CONNECT_FAILED, "connection refused");
        } catch (IOException io) {
            return failure(request, LlmErrorKind.CONNECT_FAILED, io.getClass().getSimpleName());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return failure(request, LlmErrorKind.CANCELLED, "interrupted");
        }

        OptionalLong contentLength = response.headers().firstValueAsLong("Content-Length");
        if (contentLength.isPresent() && contentLength.getAsLong() > config.maxResponseChars()) {
            return failure(request, LlmErrorKind.RESPONSE_TOO_LARGE,
                    "content-length " + contentLength.getAsLong());
        }
        if (response.statusCode() != 200) {
            return failure(request, LlmErrorKind.HTTP_ERROR, "status " + response.statusCode());
        }
        String payload = response.body();
        if (payload.length() > config.maxResponseChars()) {
            return failure(request, LlmErrorKind.RESPONSE_TOO_LARGE, "body chars " + payload.length());
        }

        OllamaEnvelope envelope;
        try {
            envelope = OllamaEnvelope.parse(payload);
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
        return new LlmResult.Failure(request.id(), kind, diagnostic);
    }
}
