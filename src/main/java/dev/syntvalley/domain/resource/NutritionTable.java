package dev.syntvalley.domain.resource;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * How much hunger (on the fixed 0..1000 need scale) each edible resource restores. A Minecraft-free
 * policy value: an item not present here is simply not food and is never eaten from storage.
 */
public record NutritionTable(Map<ResourceKey, Integer> hungerByFood) {
    public NutritionTable {
        Objects.requireNonNull(hungerByFood, "hungerByFood");
        LinkedHashMap<ResourceKey, Integer> copy = new LinkedHashMap<>();
        hungerByFood.forEach((key, restore) -> {
            Objects.requireNonNull(key, "food key");
            Objects.requireNonNull(restore, "restore");
            if (restore <= 0) {
                throw new IllegalArgumentException("nutrition must be positive for " + key);
            }
            copy.put(key, restore);
        });
        hungerByFood = Collections.unmodifiableMap(copy);
    }

    public boolean isFood(ResourceKey key) {
        return hungerByFood.containsKey(Objects.requireNonNull(key, "key"));
    }

    /** Hunger points restored by one unit of the resource, or 0 if it is not food. */
    public int restores(ResourceKey key) {
        return hungerByFood.getOrDefault(Objects.requireNonNull(key, "key"), 0);
    }
}
