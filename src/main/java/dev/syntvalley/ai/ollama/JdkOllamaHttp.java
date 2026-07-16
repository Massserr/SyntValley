package dev.syntvalley.ai.ollama;

import dev.syntvalley.ai.backend.LlmErrorKind;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * The production Ollama transport over the JDK {@link HttpClient}. It owns the connect/request deadlines
 * and maps every failure to a typed {@link OllamaHttpException}. This is the only class that touches a
 * real network; it is exercised by the optional live-Ollama check, not by the unit tests.
 */
public final class JdkOllamaHttp implements OllamaHttp {
    private final HttpClient client;
    private final Duration requestTimeout;

    public JdkOllamaHttp(OllamaConfig config) {
        Objects.requireNonNull(config, "config");
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.connectTimeoutMillis()))
                .build();
        this.requestTimeout = Duration.ofMillis(config.requestTimeoutMillis());
    }

    @Override
    public Reply send(String url, String jsonBody) throws OllamaHttpException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long declared = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            return new Reply(response.statusCode(), declared, response.body());
        } catch (HttpTimeoutException timeout) {
            throw new OllamaHttpException(LlmErrorKind.TIMEOUT, "request deadline exceeded");
        } catch (ConnectException refused) {
            throw new OllamaHttpException(LlmErrorKind.CONNECT_FAILED, "connection refused");
        } catch (IOException io) {
            throw new OllamaHttpException(LlmErrorKind.CONNECT_FAILED, io.getClass().getSimpleName());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new OllamaHttpException(LlmErrorKind.CANCELLED, "interrupted");
        }
    }
}
