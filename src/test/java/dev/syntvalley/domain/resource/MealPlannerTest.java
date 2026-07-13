package dev.syntvalley.domain.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MealPlannerTest {
    private static final ResourceKey BREAD = new ResourceKey("minecraft:bread");
    private static final ResourceKey APPLE = new ResourceKey("minecraft:apple");
    private static final ResourceKey CARROT = new ResourceKey("minecraft:carrot");
    private static final ResourceKey STONE = new ResourceKey("minecraft:stone");
    private static final NutritionTable NUTRITION = new NutritionTable(Map.of(BREAD, 400, APPLE, 200, CARROT, 200));

    private final MealPlanner planner = new MealPlanner(200);

    @Test
    void doesNotEatWhenNotHungry() {
        assertTrue(planner.choose(201, Map.of(BREAD, 5), NUTRITION).isEmpty());
    }

    @Test
    void picksMostRestorativeAvailableFood() {
        Map<ResourceKey, Integer> available = new LinkedHashMap<>();
        available.put(APPLE, 3);
        available.put(BREAD, 1);
        assertEquals(Optional.of(BREAD), planner.choose(100, available, NUTRITION));
    }

    @Test
    void breaksTiesByResourceKey() {
        Map<ResourceKey, Integer> available = new LinkedHashMap<>();
        available.put(CARROT, 2);
        available.put(APPLE, 2);
        assertEquals(Optional.of(APPLE), planner.choose(100, available, NUTRITION), "apple sorts before carrot");
    }

    @Test
    void ignoresNonFoodAndEmptyStacks() {
        Map<ResourceKey, Integer> available = new LinkedHashMap<>();
        available.put(STONE, 64);
        available.put(BREAD, 0);
        assertTrue(planner.choose(100, available, NUTRITION).isEmpty());
    }
}
