package dev.syntvalley.application.simulation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RoundRobinCursorTest {
    @Test
    void batchesWrapAndCoverFairly() {
        RoundRobinCursor cursor = new RoundRobinCursor();
        assertArrayEquals(new int[] {0, 1}, cursor.nextBatch(5, 2));
        assertArrayEquals(new int[] {2, 3}, cursor.nextBatch(5, 2));
        assertArrayEquals(new int[] {4, 0}, cursor.nextBatch(5, 2));
        assertArrayEquals(new int[] {1, 2}, cursor.nextBatch(5, 2));
    }

    @Test
    void budgetIsCappedBySizeAndGuardsEmptyInputs() {
        RoundRobinCursor cursor = new RoundRobinCursor();
        assertArrayEquals(new int[] {0, 1, 2}, cursor.nextBatch(3, 10));
        assertEquals(0, cursor.nextBatch(0, 5).length);
        assertEquals(0, cursor.nextBatch(5, 0).length);
    }
}
