package dev.syntvalley.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.syntvalley.domain.identity.VillageId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PendingConsoleLinksTest {
    private static final UUID PLAYER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final VillageId VILLAGE = new VillageId(UUID.fromString("22222222-2222-2222-2222-222222222222"));

    @Test
    void selectThenConsumeReturnsSelectionOnce() {
        PendingConsoleLinks links = new PendingConsoleLinks();
        assertTrue(links.select(PLAYER, VILLAGE, 100));

        assertEquals(VILLAGE, links.consume(PLAYER, 50).orElseThrow());
        assertEquals(0, links.size(), "selection is consumed exactly once");
        assertTrue(links.consume(PLAYER, 50).isEmpty(), "second consume finds nothing");
    }

    @Test
    void expiredSelectionIsNotConsumable() {
        PendingConsoleLinks links = new PendingConsoleLinks();
        links.select(PLAYER, VILLAGE, 100);

        assertTrue(links.consume(PLAYER, 101).isEmpty(), "expired selection returns empty");
        assertEquals(0, links.size(), "expired selection is removed on consume");
    }

    @Test
    void missingSelectionReturnsEmpty() {
        PendingConsoleLinks links = new PendingConsoleLinks();
        assertTrue(links.consume(PLAYER, 0).isEmpty());
    }

    @Test
    void capacityRejectsNewPlayersButAllowsReselect() {
        PendingConsoleLinks links = new PendingConsoleLinks(1);
        assertTrue(links.select(PLAYER, VILLAGE, 100));
        assertFalse(links.select(OTHER, VILLAGE, 100), "registry is full for a new player");
        assertTrue(links.select(PLAYER, VILLAGE, 200), "existing player may reselect");
    }

    @Test
    void clearRemovesSelection() {
        PendingConsoleLinks links = new PendingConsoleLinks();
        links.select(PLAYER, VILLAGE, 100);
        assertTrue(links.clear(PLAYER));
        assertTrue(links.consume(PLAYER, 50).isEmpty());
    }
}
