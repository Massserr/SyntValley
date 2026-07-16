package dev.syntvalley.ai.protocol.json;

/**
 * Raised for any lexical, structural or cap violation. The protocol layer maps every one of these to a
 * single {@code MALFORMED_JSON} rejection — the specific reason is for bounded diagnostics only.
 */
public final class StrictJsonException extends RuntimeException {
    public StrictJsonException(String reason) {
        super(reason);
    }
}
