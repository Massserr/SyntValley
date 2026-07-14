package dev.syntvalley.domain.personality;

/**
 * A derived, non-persistent mood bucket. It is computed from current needs (and later events/memories),
 * and only shifts soft task scores — it never persists as canonical state and never overrides safety.
 */
public enum Mood {
    LOW,
    NEUTRAL,
    HIGH;

    /** Buckets the average of the two 0..1000 needs into a mood. */
    public static Mood fromNeeds(int hunger, int rest) {
        int average = (hunger + rest) / 2;
        if (average < 300) {
            return LOW;
        }
        if (average < 700) {
            return NEUTRAL;
        }
        return HIGH;
    }
}
