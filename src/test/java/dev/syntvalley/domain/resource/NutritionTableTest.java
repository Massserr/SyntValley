package dev.syntvalley.domain.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NutritionTableTest {
    private static final ResourceKey BREAD = new ResourceKey("minecraft:bread");
    private static final ResourceKey STONE = new ResourceKey("minecraft:stone");

    @Test
    void reportsFoodAndRestoreValue() {
        NutritionTable table = new NutritionTable(Map.of(BREAD, 400));
        assertTrue(table.isFood(BREAD));
        assertEquals(400, table.restores(BREAD));
        assertFalse(table.isFood(STONE));
        assertEquals(0, table.restores(STONE));
    }

    @Test
    void rejectsNonPositiveNutrition() {
        Map<ResourceKey, Integer> foods = new LinkedHashMap<>();
        foods.put(BREAD, 0);
        assertThrows(IllegalArgumentException.class, () -> new NutritionTable(foods));
    }

    @Test
    void copiesAndFreezesItsInput() {
        Map<ResourceKey, Integer> foods = new LinkedHashMap<>();
        foods.put(BREAD, 400);
        NutritionTable table = new NutritionTable(foods);
        foods.put(STONE, 10);
        assertFalse(table.isFood(STONE), "table must copy its input");
        assertThrows(UnsupportedOperationException.class, () -> table.hungerByFood().put(STONE, 5));
    }
}
