package dev.syntvalley.domain.need;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NeedsTest {
    @Test
    void criticalDetectionAndDeterministicTieBreak() {
        assertFalse(new Needs(500, 500, 0).isCritical());
        assertTrue(new Needs(150, 500, 0).mostCritical().isEmpty() == false);
        assertEquals(NeedKind.HUNGER, new Needs(150, 500, 0).mostCritical().orElseThrow());
        assertEquals(NeedKind.REST, new Needs(100, 50, 0).mostCritical().orElseThrow(), "lower value wins");
        assertTrue(new Needs(500, 500, 0).mostCritical().isEmpty());
    }

    @Test
    void replenishClampsToMax() {
        Needs fed = new Needs(100, 100, 0).replenish(NeedKind.HUNGER, 950);
        assertEquals(NeedBounds.MAX, fed.hunger());
        assertEquals(100, fed.rest());
    }
}
