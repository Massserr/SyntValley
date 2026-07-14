package dev.syntvalley.domain.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class MemoryStoreTest {
    private static MemoryRecord rec(String key, int salience, long time) {
        return new MemoryRecord(key, MemoryKind.PLAYER_HELPED, MemorySource.OBSERVED, "", salience, time, false);
    }

    @Test
    void addIsIdempotentByKey() {
        MemoryStore store = new MemoryStore(8, 2);
        assertTrue(store.add(rec("a", 100, 1)));
        assertFalse(store.add(rec("a", 500, 2)), "same event id is not remembered twice");
        assertEquals(1, store.size());
        assertEquals(100, store.find("a").orElseThrow().salience());
    }

    @Test
    void decayReducesThenForgets() {
        MemoryStore store = new MemoryStore(8, 2);
        store.add(rec("a", 100, 1));
        store.decay(40);
        assertEquals(60, store.find("a").orElseThrow().salience());
        store.decay(60);
        assertTrue(store.find("a").isEmpty(), "a memory that fades to zero is forgotten");
        assertEquals(0, store.size());
    }

    @Test
    void pinnedSurviveDecay() {
        MemoryStore store = new MemoryStore(8, 2);
        store.add(rec("a", 50, 1));
        assertTrue(store.pin("a"));
        store.decay(1000);
        assertEquals(50, store.find("a").orElseThrow().salience(), "pinned memories never decay");
    }

    @Test
    void evictsLeastSalientUnpinnedOverCapacity() {
        MemoryStore store = new MemoryStore(2, 1);
        store.add(rec("a", 100, 1));
        store.add(rec("b", 50, 2));
        store.add(rec("c", 80, 3));
        assertEquals(2, store.size());
        assertTrue(store.find("b").isEmpty(), "the least salient un-pinned memory is evicted");
        assertTrue(store.find("a").isPresent());
        assertTrue(store.find("c").isPresent());
    }

    @Test
    void pinnedAreNeverEvictedEvenWhenLeastSalient() {
        MemoryStore store = new MemoryStore(2, 2);
        store.add(rec("a", 30, 1));
        assertTrue(store.pin("a"));
        store.add(rec("b", 90, 2));
        store.add(rec("c", 80, 3));
        assertTrue(store.find("a").isPresent(), "a pinned low-salience memory outlives higher-salience ones");
        assertTrue(store.find("c").isEmpty());
    }

    @Test
    void pinIsRefusedBeyondCap() {
        MemoryStore store = new MemoryStore(4, 1);
        store.add(rec("a", 10, 1));
        store.add(rec("b", 20, 2));
        assertTrue(store.pin("a"));
        assertFalse(store.pin("b"), "the pinned cap is enforced");
    }

    @Test
    void rankedPutsPinnedThenMostSalientFirst() {
        MemoryStore store = new MemoryStore(8, 2);
        store.add(rec("low", 40, 1));
        store.add(rec("high", 90, 2));
        store.add(rec("pinnedMid", 60, 3));
        store.pin("pinnedMid");

        List<MemoryRecord> ranked = store.ranked();
        assertEquals("pinnedMid", ranked.get(0).dedupeKey());
        assertEquals("high", ranked.get(1).dedupeKey());
        assertEquals("low", ranked.get(2).dedupeKey());
    }
}
