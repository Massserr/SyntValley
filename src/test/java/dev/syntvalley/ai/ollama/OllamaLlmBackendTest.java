package dev.syntvalley.ai.ollama;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.syntvalley.ai.backend.LlmErrorKind;
import dev.syntvalley.ai.backend.LlmRequest;
import dev.syntvalley.ai.backend.LlmResult;
import org.junit.jupiter.api.Test;

/**
 * Classification tests against a fake transport — no socket, so they are deterministic and run anywhere.
 * The real JDK transport is covered only by the optional live-Ollama check.
 */
class OllamaLlmBackendTest {
    private static final OllamaConfig CONFIG =
            new OllamaConfig("http://127.0.0.1:11434", "qwen3:8b", 1_000, 500, 4_096);

    private static OllamaHttp reply(int status, long declaredLength, String body) {
        return (url, jsonBody) -> new OllamaHttp.Reply(status, declaredLength, body);
    }

    private static OllamaHttp throwing(LlmErrorKind kind) {
        return (url, jsonBody) -> {
            throw new OllamaHttpException(kind, "boom");
        };
    }

    private static LlmResult generate(OllamaHttp http) {
        return new OllamaLlmBackend(CONFIG, http).generate(LlmRequest.of("test", "hello"));
    }

    private static LlmResult.Failure expectFailure(OllamaHttp http, LlmErrorKind kind) {
        LlmResult.Failure failure = assertInstanceOf(LlmResult.Failure.class, generate(http));
        assertEquals(kind, failure.kind());
        return failure;
    }

    @Test
    void successReturnsUnicodeContent() {
        LlmResult result = generate(reply(200, -1, "{\"model\":\"m\",\"response\":\"Привет из деревни\",\"done\":true}"));
        LlmResult.Success success = assertInstanceOf(LlmResult.Success.class, result);
        assertEquals("Привет из деревни", success.response().content());
    }

    @Test
    void non200IsHttpError() {
        expectFailure(reply(503, -1, "busy"), LlmErrorKind.HTTP_ERROR);
    }

    @Test
    void malformedJsonIsClassified() {
        expectFailure(reply(200, -1, "{not json at all"), LlmErrorKind.MALFORMED_RESPONSE);
    }

    @Test
    void notDoneEnvelopeIsMalformed() {
        expectFailure(reply(200, -1, "{\"response\":\"partial\",\"done\":false}"), LlmErrorKind.MALFORMED_RESPONSE);
    }

    @Test
    void declaredLengthOverCapIsRejectedBeforeReading() {
        expectFailure(reply(200, 999_999, "ignored"), LlmErrorKind.RESPONSE_TOO_LARGE);
    }

    @Test
    void oversizedBodyIsRejected() {
        String huge = "{\"response\":\"" + "x".repeat(5_000) + "\",\"done\":true}";
        expectFailure(reply(200, -1, huge), LlmErrorKind.RESPONSE_TOO_LARGE);
    }

    @Test
    void transportTimeoutAndConnectFailureAreTyped() {
        expectFailure(throwing(LlmErrorKind.TIMEOUT), LlmErrorKind.TIMEOUT);
        expectFailure(throwing(LlmErrorKind.CONNECT_FAILED), LlmErrorKind.CONNECT_FAILED);
    }

    @Test
    void diagnosticsNeverContainThePrompt() {
        OllamaHttp http = reply(500, -1, "nope");
        LlmResult result = new OllamaLlmBackend(CONFIG, http)
                .generate(LlmRequest.of("test", "SECRET-PROMPT-TEXT"));
        LlmResult.Failure failure = assertInstanceOf(LlmResult.Failure.class, result);
        assertFalse(failure.diagnostic().contains("SECRET-PROMPT-TEXT"), "diagnostics must stay prompt-free");
    }
}
