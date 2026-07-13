package dev.syntvalley.domain.need;

import java.util.Objects;

/**
 * Deterministic elapsed-time need decay. Only whole intervals are consumed; the sub-interval
 * remainder is carried forward via {@code lastUpdatedGameTime}, so the result is independent of how
 * often the policy is invoked.
 */
public final class NeedUpdatePolicy {
    private NeedUpdatePolicy() {
    }

    public static Needs advance(Needs needs, long now, NeedDecayRates rates) {
        Objects.requireNonNull(needs, "needs");
        Objects.requireNonNull(rates, "rates");

        long elapsed = now - needs.lastUpdatedGameTime();
        if (elapsed <= 0) {
            return needs;
        }
        long intervals = elapsed / rates.intervalTicks();
        if (intervals <= 0) {
            return needs;
        }

        long consumedTicks = intervals * rates.intervalTicks();
        int hungerLost = (int) Math.min((long) NeedBounds.MAX, intervals * rates.hungerPerInterval());
        int restLost = (int) Math.min((long) NeedBounds.MAX, intervals * rates.restPerInterval());
        return needs.decayed(hungerLost, restLost, needs.lastUpdatedGameTime() + consumedTicks);
    }
}
