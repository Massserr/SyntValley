package dev.syntvalley.domain.decision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class DecisionLogTest {
    private static DecisionRecord add(DecisionLog log, long time) {
        return log.record(DecisionKind.CITIZEN_TASK, "citizen", "REST", DecisionSource.DETERMINISTIC, "tired", time);
    }

    @Test
    void recordsGetIncreasingSequences() {
        DecisionLog log = new DecisionLog(10);
        assertEquals(1, add(log, 1).sequence());
        assertEquals(2, add(log, 2).sequence());
        assertEquals(3, add(log, 3).sequence());
    }

    @Test
    void keepsOnlyTheMostRecentWithinCap() {
        DecisionLog log = new DecisionLog(2);
        add(log, 1);
        add(log, 2);
        add(log, 3);
        assertEquals(2, log.size());
        List<DecisionRecord> all = log.all();
        assertEquals(2, all.get(0).sequence(), "the oldest entry was dropped");
        assertEquals(3, all.get(1).sequence());
    }

    @Test
    void recentReturnsNewestFirstUpToLimit() {
        DecisionLog log = new DecisionLog(10);
        add(log, 1);
        add(log, 2);
        add(log, 3);
        List<DecisionRecord> recent = log.recent(2);
        assertEquals(3, recent.get(0).sequence());
        assertEquals(2, recent.get(1).sequence());
    }

    @Test
    void pageWalksOlderEntriesByCursor() {
        DecisionLog log = new DecisionLog(10);
        for (int i = 1; i <= 5; i++) {
            add(log, i);
        }
        List<DecisionRecord> firstPage = log.recent(2);
        assertEquals(5, firstPage.get(0).sequence());
        long cursor = firstPage.get(firstPage.size() - 1).sequence();
        List<DecisionRecord> secondPage = log.page(cursor, 2);
        assertEquals(3, secondPage.get(0).sequence());
        assertEquals(2, secondPage.get(1).sequence());
    }

    @Test
    void rejectsNonPositiveCap() {
        assertThrows(IllegalArgumentException.class, () -> new DecisionLog(0));
    }

    @Test
    void restoreContinuesSequencesAfterTheHighest() {
        DecisionLog original = new DecisionLog(10);
        add(original, 1);
        add(original, 2);

        DecisionLog restored = DecisionLog.restore(10, original.all());
        assertEquals(2, restored.size());
        assertEquals(3, add(restored, 3).sequence(), "sequence continues after restore, cursors stay stable");
    }
}
