package dev.syntvalley.application.service;

import dev.syntvalley.domain.resource.ResourceKey;
import java.util.Objects;
import java.util.Optional;

/**
 * The result of trying to feed a hungry citizen from village storage. FED is the only outcome that
 * moves an item and restores hunger; NO_FOOD and RESERVED leave the world untouched and simply retry
 * later; STALE means the ledger was out of date and the physical stack was already gone.
 */
public record MealOutcome(Result result, Optional<ResourceKey> food, int hungerRestored) {
    public enum Result { FED, NO_FOOD, RESERVED, STALE }

    public MealOutcome {
        Objects.requireNonNull(result, "result");
        food = Objects.requireNonNull(food, "food");
        if (hungerRestored < 0) {
            throw new IllegalArgumentException("hungerRestored must not be negative");
        }
    }

    public static MealOutcome fed(ResourceKey food, int hungerRestored) {
        return new MealOutcome(Result.FED, Optional.of(food), hungerRestored);
    }

    public static MealOutcome noFood() {
        return new MealOutcome(Result.NO_FOOD, Optional.empty(), 0);
    }

    public static MealOutcome reserved() {
        return new MealOutcome(Result.RESERVED, Optional.empty(), 0);
    }

    public static MealOutcome stale() {
        return new MealOutcome(Result.STALE, Optional.empty(), 0);
    }

    public boolean fed() {
        return result == Result.FED;
    }
}
