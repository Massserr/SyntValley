package dev.syntvalley.application.resource;

import dev.syntvalley.domain.resource.NutritionTable;
import dev.syntvalley.domain.resource.ResourceKey;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The built-in foods a citizen will eat from storage and how much hunger (on the 0..1000 scale) each
 * unit restores. Java-registered like the profession catalog; a datapack-driven table can replace this
 * later without changing callers.
 */
public final class NutritionCatalog {
    private NutritionCatalog() {
    }

    public static NutritionTable builtin() {
        Map<ResourceKey, Integer> foods = new LinkedHashMap<>();
        foods.put(new ResourceKey("minecraft:cooked_beef"), 500);
        foods.put(new ResourceKey("minecraft:cooked_porkchop"), 500);
        foods.put(new ResourceKey("minecraft:bread"), 400);
        foods.put(new ResourceKey("minecraft:cooked_chicken"), 350);
        foods.put(new ResourceKey("minecraft:cooked_mutton"), 350);
        foods.put(new ResourceKey("minecraft:baked_potato"), 300);
        foods.put(new ResourceKey("minecraft:apple"), 200);
        foods.put(new ResourceKey("minecraft:carrot"), 200);
        return new NutritionTable(foods);
    }
}
