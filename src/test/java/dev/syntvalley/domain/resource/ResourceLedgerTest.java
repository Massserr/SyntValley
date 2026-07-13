package dev.syntvalley.domain.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResourceLedgerTest {
    private static final ResourceKey OAK = new ResourceKey("minecraft:oak_log");
    private static final ResourceKey STONE = new ResourceKey("minecraft:stone");

    @Test
    void countsPresentAndAbsentKeys() {
        ResourceLedger ledger = new ResourceLedger(Map.of(OAK, 5), 100);
        assertEquals(5, ledger.count(OAK));
        assertEquals(0, ledger.count(STONE));
        assertEquals(0, ResourceLedger.empty(0).count(OAK));
    }

    @Test
    void dropsZeroCountsAndRejectsNegative() {
        assertEquals(0, new ResourceLedger(Map.of(OAK, 0), 0).count(OAK));

        Map<ResourceKey, Integer> negative = new HashMap<>();
        negative.put(OAK, -1);
        assertThrows(IllegalArgumentException.class, () -> new ResourceLedger(negative, 0));
    }
}
