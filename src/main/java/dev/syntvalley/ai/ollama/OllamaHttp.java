package dev.syntvalley.ai.ollama;

/**
 * The single network seam of the Ollama adapter. Isolating the raw HTTP send here lets the backend's
 * status/size/parse classification be unit-tested against a fake transport — no real socket, so the
 * tests are deterministic and run anywhere (a live-server test is manual and never required).
 */
public interface OllamaHttp {
    /** Sends {@code jsonBody} to {@code url}; returns the raw reply or throws a classified transport failure. */
    Reply send(String url, String jsonBody) throws OllamaHttpException;

    /** A raw HTTP reply. {@code declaredLength} is the Content-Length, or -1 when the server omits it. */
    record Reply(int status, long declaredLength, String body) {
        public Reply {
            if (body == null) {
                throw new IllegalArgumentException("body must not be null");
            }
        }
    }
}
