package dev.syntvalley.ai.ollama;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
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

        JsonObject body = new JsonObject();
        body.addProperty("model", config.model());
        body.addProperty("prompt", request.prompt());
        body.addProperty("stream", false);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(config.baseUrl() + "/api/generate"))
                .timeout(Duration.ofMillis(config.requestTimeoutMillis()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
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

        try {
            JsonObject envelope = JsonParser.parseString(payload).getAsJsonObject();
            if (!envelope.has("done") || !envelope.get("done").getAsBoolean()) {
                return failure(request, LlmErrorKind.MALFORMED_RESPONSE, "missing or false done flag");
            }
            if (!envelope.has("response") || !envelope.get("response").isJsonPrimitive()) {
                return failure(request, LlmErrorKind.MALFORMED_RESPONSE, "missing response field");
            }
            String content = envelope.get("response").getAsString();
            if (content.length() > LlmResponse.MAX_CONTENT_CHARS) {
                return failure(request, LlmErrorKind.RESPONSE_TOO_LARGE, "response chars " + content.length());
            }
            return new LlmResult.Success(
                    new LlmResponse(request.id(), content, System.currentTimeMillis() - startedAt));
        } catch (JsonParseException | IllegalStateException | UnsupportedOperationException malformed) {
            return failure(request, LlmErrorKind.MALFORMED_RESPONSE, malformed.getClass().getSimpleName());
        }
    }

    @Override
    public String name() {
        return "ollama(" + config.model() + ")";
    }

    private static LlmResult failure(LlmRequest request, LlmErrorKind kind, String diagnostic) {
        return new LlmResult.Failure(request.id(), kind, diagnostic);
    }
}
