package dev.syntvalley.domain.need;

/** Deterministic decay: each {@code intervalTicks} a need loses a fixed number of points. */
public record NeedDecayRates(int intervalTicks, int hungerPerInterval, int restPerInterval) {
    public NeedDecayRates {
        if (intervalTicks < 1) {
            throw new IllegalArgumentException("intervalTicks must be positive");
        }
        if (hungerPerInterval < 0 || restPerInterval < 0) {
            throw new IllegalArgumentException("decay per interval must not be negative");
        }
    }

    public static NeedDecayRates defaults() {
        return new NeedDecayRates(100, 5, 3);
    }
}
