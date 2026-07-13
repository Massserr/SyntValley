package dev.syntvalley.domain.resource;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Picks what a hungry citizen should eat: only when hunger is at or below the threshold, and only among
 * foods that are physically available (count &gt; 0). Deterministic — it prefers the most restorative
 * food and breaks ties by resource key so every server reaches the same choice.
 */
public final class MealPlanner {
    private final int hungerThreshold;

    public MealPlanner(int hungerThreshold) {
        if (hungerThreshold < 0) {
            throw new IllegalArgumentException("hungerThreshold must not be negative");
        }
        this.hungerThreshold = hungerThreshold;
    }

    public boolean isHungry(int hunger) {
        return hunger <= hungerThreshold;
    }

    public Optional<ResourceKey> choose(int hunger, Map<ResourceKey, Integer> available, NutritionTable nutrition) {
        Objects.requireNonNull(available, "available");
        Objects.requireNonNull(nutrition, "nutrition");
        if (!isHungry(hunger)) {
            return Optional.empty();
        }
        ResourceKey best = null;
        int bestRestore = 0;
        for (Map.Entry<ResourceKey, Integer> entry : available.entrySet()) {
            ResourceKey key = entry.getKey();
            int count = entry.getValue() == null ? 0 : entry.getValue();
            if (count <= 0 || !nutrition.isFood(key)) {
                continue;
            }
            int restore = nutrition.restores(key);
            boolean better = best == null
                    || restore > bestRestore
                    || (restore == bestRestore && key.value().compareTo(best.value()) < 0);
            if (better) {
                best = key;
                bestRestore = restore;
            }
        }
        return Optional.ofNullable(best);
    }

    public int hungerThreshold() {
        return hungerThreshold;
    }
}
