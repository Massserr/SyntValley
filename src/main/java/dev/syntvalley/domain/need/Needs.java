package dev.syntvalley.domain.need;

import java.util.Optional;

/** Immutable citizen needs on the fixed 0..1000 scale, with the game time of the last update. */
public record Needs(int hunger, int rest, long lastUpdatedGameTime) {
    public Needs {
        if (!NeedBounds.isValid(hunger)) {
            throw new IllegalArgumentException("hunger out of range: " + hunger);
        }
        if (!NeedBounds.isValid(rest)) {
            throw new IllegalArgumentException("rest out of range: " + rest);
        }
        if (lastUpdatedGameTime < 0) {
            throw new IllegalArgumentException("lastUpdatedGameTime must not be negative");
        }
    }

    public static Needs full(long gameTime) {
        return new Needs(NeedBounds.MAX, NeedBounds.MAX, gameTime);
    }

    public int of(NeedKind kind) {
        return kind == NeedKind.HUNGER ? hunger : rest;
    }

    public boolean isCritical() {
        return hunger <= NeedBounds.CRITICAL_THRESHOLD || rest <= NeedBounds.CRITICAL_THRESHOLD;
    }

    /** The most depleted critical need, or empty when none is critical. Deterministic tie-break. */
    public Optional<NeedKind> mostCritical() {
        boolean hungerCritical = hunger <= NeedBounds.CRITICAL_THRESHOLD;
        boolean restCritical = rest <= NeedBounds.CRITICAL_THRESHOLD;
        if (!hungerCritical && !restCritical) {
            return Optional.empty();
        }
        if (hungerCritical && restCritical) {
            return Optional.of(hunger <= rest ? NeedKind.HUNGER : NeedKind.REST);
        }
        return Optional.of(hungerCritical ? NeedKind.HUNGER : NeedKind.REST);
    }

    public Needs replenish(NeedKind kind, int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("replenish amount must not be negative");
        }
        return kind == NeedKind.HUNGER
                ? new Needs(NeedBounds.clamp(hunger + amount), rest, lastUpdatedGameTime)
                : new Needs(hunger, NeedBounds.clamp(rest + amount), lastUpdatedGameTime);
    }

    Needs decayed(int hungerLost, int restLost, long newLastUpdatedGameTime) {
        return new Needs(
                NeedBounds.clamp(hunger - hungerLost),
                NeedBounds.clamp(rest - restLost),
                newLastUpdatedGameTime);
    }
}
