package dev.syntvalley.domain.resource;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A bounded snapshot cache of resource counts aggregated from the village's explicitly linked
 * storages. It is a cache, not the source of truth: the real ItemStacks are re-checked at execution.
 */
public record ResourceLedger(Map<ResourceKey, Integer> counts, long lastUpdatedGameTime) {
    public ResourceLedger {
        Objects.requireNonNull(counts, "counts");
        if (lastUpdatedGameTime < 0) {
            throw new IllegalArgumentException("lastUpdatedGameTime must not be negative");
        }
        LinkedHashMap<ResourceKey, Integer> copy = new LinkedHashMap<>();
        counts.forEach((key, amount) -> {
            Objects.requireNonNull(key, "resource key");
            Objects.requireNonNull(amount, "amount");
            if (amount < 0) {
                throw new IllegalArgumentException("resource count must not be negative");
            }
            if (amount > 0) {
                copy.put(key, amount);
            }
        });
        counts = Collections.unmodifiableMap(copy);
    }

    public static ResourceLedger empty(long gameTime) {
        return new ResourceLedger(Map.of(), gameTime);
    }

    public int count(ResourceKey key) {
        return counts.getOrDefault(Objects.requireNonNull(key, "key"), 0);
    }
}
