package dev.syntvalley.ai.ollama;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import dev.syntvalley.ai.backend.LlmErrorKind;
import dev.syntvalley.ai.backend.LlmRequest;
import dev.syntvalley.ai.backend.LlmResult;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OllamaLlmBackendTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private OllamaConfig startServer(String responseBody, int status, long delayMillis) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/generate", exchange -> {
            try {
                if (delayMillis > 0) {
                    Thread.sleep(delayMillis);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        return new OllamaConfig(
                "http://127.0.0.1:" + server.getAddress().getPort(), "qwen3:8b", 1_000, 500, 4_096);
    }

    private static LlmResult.Failure expectFailure(OllamaConfig config, LlmErrorKind kind) {
        LlmResult result = new OllamaLlmBackend(config).generate(LlmRequest.of("test", "hello"));
        LlmResult.Failure failure = assertInstanceOf(LlmResult.Failure.class, result);
        assertEquals(kind, failure.kind());
        return failure;
    }

    @Test
    void successfulGenerationReturnsContent() throws IOException {
        OllamaConfig config = startServer("{\"response\":\"Привет из деревни\",\"done\":true}", 200, 0);
        LlmResult result = new OllamaLlmBackend(config).generate(LlmRequest.of("diagnostic", "ping"));
        LlmResult.Success success = assertInstanceOf(LlmResult.Success.class, result);
        assertEquals("Привет из деревни", success.response().content());
        assertTrue(success.response().durationMillis() >= 0);
    }

    @Test
    void non200StatusIsHttpError() throws IOException {
        expectFailure(startServer("busy", 503, 0), LlmErrorKind.HTTP_ERROR);
    }

    @Test
    void malformedJsonIsClassified() throws IOException {
        expectFailure(startServer("{not json at all", 200, 0), LlmErrorKind.MALFORMED_RESPONSE);
    }

    @Test
    void notDoneEnvelopeIsMalformed() throws IOException {
        expectFailure(startServer("{\"response\":\"partial\",\"done\":false}", 200, 0),
                LlmErrorKind.MALFORMED_RESPONSE);
    }

    @Test
    void oversizedEnvelopeIsRejected() throws IOException {
        String huge = "{\"response\":\"" + "x".repeat(8_000) + "\",\"done\":true}";
        expectFailure(startServer(huge, 200, 0), LlmErrorKind.RESPONSE_TOO_LARGE);
    }

    @Test
    void slowBackendHitsTheDeadline() throws IOException {
        expectFailure(startServer("{\"response\":\"late\",\"done\":true}", 200, 5_000), LlmErrorKind.TIMEOUT);
    }

    @Test
    void closedPortIsConnectFailed() throws IOException {
        HttpServer probe = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int freePort = probe.getAddress().getPort();
        probe.stop(0); // the port is now closed
        OllamaConfig config = new OllamaConfig("http://127.0.0.1:" + freePort, "qwen3:8b", 500, 500, 4_096);
        expectFailure(config, LlmErrorKind.CONNECT_FAILED);
    }

    @Test
    void diagnosticsNeverContainThePrompt() throws IOException {
        OllamaConfig config = startServer("nope", 500, 0);
        LlmResult result = new OllamaLlmBackend(config)
                .generate(LlmRequest.of("test", "SECRET-PROMPT-TEXT"));
        LlmResult.Failure failure = assertInstanceOf(LlmResult.Failure.class, result);
        assertTrue(!failure.diagnostic().contains("SECRET-PROMPT-TEXT"),
                "diagnostics must stay prompt-free");
    }
}
