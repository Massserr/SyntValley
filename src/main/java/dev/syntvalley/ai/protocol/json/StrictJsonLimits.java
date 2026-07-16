package dev.syntvalley.ai.protocol.json;

/**
 * Hard structural caps applied while parsing, before a full object graph exists (protocol §5). Defaults
 * are the v1 contract values; they may only be tightened, never loosened, by callers.
 */
public record StrictJsonLimits(int maxChars, int maxDepth, int maxProperties) {
    public StrictJsonLimits {
        if (maxChars < 2 || maxDepth < 1 || maxProperties < 1) {
            throw new IllegalArgumentException("limits must be positive and allow at least an empty object");
        }
    }

    /** v1 defaults: 64 KiB, depth 8, 32 properties per object. */
    public static StrictJsonLimits v1() {
        return new StrictJsonLimits(64 * 1024, 8, 32);
    }
}
