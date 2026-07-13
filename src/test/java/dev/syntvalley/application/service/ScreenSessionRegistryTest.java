package dev.syntvalley.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.syntvalley.domain.identity.VillageId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScreenSessionRegistryTest {
    private static final UUID VIEWER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final VillageId VILLAGE = new VillageId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
    private static final String DIM = "minecraft:overworld";
    private static final long POS = 274_877_911_104L;

    @Test
    void openFindMatchAndClose() {
        ScreenSessionRegistry registry = new ScreenSessionRegistry();
        assertInstanceOf(ScreenSessionRegistry.OpenResult.Opened.class, registry.open(VIEWER, VILLAGE, DIM, POS, 10));

        assertTrue(registry.find(VIEWER).isPresent());
        assertTrue(registry.matches(VIEWER, VILLAGE, DIM, POS), "session should match its own target");
        assertFalse(registry.matches(VIEWER, VILLAGE, "minecraft:the_nether", POS), "wrong dimension must not match");

        assertTrue(registry.close(VIEWER));
        assertTrue(registry.find(VIEWER).isEmpty());
    }

    @Test
    void capacityRejectsNewViewersButAllowsReopen() {
        ScreenSessionRegistry registry = new ScreenSessionRegistry(1);
        assertInstanceOf(ScreenSessionRegistry.OpenResult.Opened.class, registry.open(VIEWER, VILLAGE, DIM, POS, 0));
        assertInstanceOf(ScreenSessionRegistry.OpenResult.Rejected.class, registry.open(OTHER, VILLAGE, DIM, POS, 0));
        assertInstanceOf(ScreenSessionRegistry.OpenResult.Opened.class, registry.open(VIEWER, VILLAGE, DIM, POS, 1));
        assertEquals(1, registry.size());
    }

    @Test
    void rateGateBlocksUntilIntervalElapses() {
        ScreenSessionRegistry registry = new ScreenSessionRegistry();
        registry.open(VIEWER, VILLAGE, DIM, POS, 0);

        assertFalse(registry.canServe(VIEWER, 3, 5), "should be gated before the interval");
        assertTrue(registry.canServe(VIEWER, 5, 5), "should be servable once the interval elapses");
        registry.markServed(VIEWER, 7L, 5);
        assertEquals(7L, registry.find(VIEWER).orElseThrow().lastServedRevision());
        assertFalse(registry.canServe(VIEWER, 8, 5), "activity resets the rate gate");
    }

    @Test
    void expireRemovesIdleSessions() {
        ScreenSessionRegistry registry = new ScreenSessionRegistry();
        registry.open(VIEWER, VILLAGE, DIM, POS, 0);

        assertTrue(registry.expire(100, 50).contains(VIEWER));
        assertEquals(0, registry.size());
    }
}
