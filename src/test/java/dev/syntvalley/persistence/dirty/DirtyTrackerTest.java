package dev.syntvalley.persistence.dirty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.syntvalley.domain.identity.VillageId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DirtyTrackerTest {
    @Test
    void coalescesReasonsWithoutDroppingRootDirtySignal() {
        AtomicInteger dirtySignals = new AtomicInteger();
        DirtyTracker tracker = new DirtyTracker(2, dirtySignals::incrementAndGet);
        DirtyKey key = DirtyKey.village(new VillageId(UUID.fromString("c96bc8af-f4eb-46f5-bc8b-d1ef54eb4a39")));

        tracker.mark(key, DirtyReason.CREATED);
        tracker.mark(key, DirtyReason.CORE_ORPHANED);

        assertEquals(1, tracker.pendingCount());
        assertEquals(2, dirtySignals.get());
        DirtyEntry entry = tracker.drainAll().getFirst();
        assertEquals(key, entry.key());
        assertTrue(entry.reasons().containsAll(List.of(DirtyReason.CREATED, DirtyReason.CORE_ORPHANED)));
        assertEquals(0, tracker.pendingCount());
    }

    @Test
    void capacityFailureDoesNotDropExistingEntryAndFullDrainEmptiesTracker() {
        DirtyTracker tracker = new DirtyTracker(1, () -> { });
        DirtyKey first = DirtyKey.village(new VillageId(UUID.fromString("da222366-021b-42d7-804d-667f84fbdeee")));
        DirtyKey second = DirtyKey.village(new VillageId(UUID.fromString("c63fb516-cd26-4a34-bfb0-c70ec8ad047b")));
        tracker.mark(first, DirtyReason.CREATED);

        assertThrows(IllegalStateException.class, () -> tracker.mark(second, DirtyReason.CREATED));
        assertEquals(first, tracker.drainAll().getFirst().key());
        assertEquals(0, tracker.pendingCount());
    }
}
