package dev.syntvalley.domain.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MemoryRecordTest {
    private static MemoryRecord record(int salience) {
        return new MemoryRecord("k", MemoryKind.PROJECT_COMPLETED, MemorySource.OBSERVED, "proj", salience, 10, false);
    }

    @Test
    void rejectsBlankKeyBadSalienceAndNegativeTime() {
        assertThrows(IllegalArgumentException.class,
                () -> new MemoryRecord(" ", MemoryKind.PLAYER_FED_CITIZEN, MemorySource.OBSERVED, "c", 10, 0, false));
        assertThrows(IllegalArgumentException.class, () -> record(-1));
        assertThrows(IllegalArgumentException.class, () -> record(MemoryRecord.MAX_SALIENCE + 1));
        assertThrows(IllegalArgumentException.class,
                () -> new MemoryRecord("k", MemoryKind.CITIZEN_DIED, MemorySource.OBSERVED, "c", 10, -1, false));
    }

    @Test
    void withSalienceClampsToBounds() {
        assertEquals(MemoryRecord.MAX_SALIENCE, record(100).withSalience(5000).salience());
        assertEquals(MemoryRecord.MIN_SALIENCE, record(100).withSalience(-5000).salience());
    }

    @Test
    void onlyObservedIsAFact() {
        assertTrue(MemorySource.OBSERVED.isObservedFact());
        assertFalse(MemorySource.PLAYER_SAID.isObservedFact());
        assertFalse(MemorySource.LLM_SUGGESTED.isObservedFact());
    }
}
