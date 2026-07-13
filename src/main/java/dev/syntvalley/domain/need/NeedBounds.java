package dev.syntvalley.domain.need;

/** Fixed integer need scale shared by the domain and the schema-1 persistence codec. */
public final class NeedBounds {
    public static final int MIN = 0;
    public static final int MAX = 1000;

    /** At or below this a need is critical and preempts soft activities. */
    public static final int CRITICAL_THRESHOLD = 200;

    private NeedBounds() {
    }

    public static int clamp(int value) {
        return Math.max(MIN, Math.min(MAX, value));
    }

    public static boolean isValid(int value) {
        return value >= MIN && value <= MAX;
    }
}
