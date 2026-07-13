package dev.syntvalley.application.simulation;

/** Fair round-robin cursor over an ordered set of a given size, handing out bounded batches. */
public final class RoundRobinCursor {
    private int position;

    /** Up to {@code budget} indices in {@code [0, size)}, starting where the last batch ended. */
    public int[] nextBatch(int size, int budget) {
        if (size <= 0 || budget <= 0) {
            return new int[0];
        }
        int start = Math.floorMod(position, size);
        int count = Math.min(budget, size);
        int[] batch = new int[count];
        for (int index = 0; index < count; index++) {
            batch[index] = (start + index) % size;
        }
        position = (start + count) % size;
        return batch;
    }

    public int position() {
        return position;
    }
}
