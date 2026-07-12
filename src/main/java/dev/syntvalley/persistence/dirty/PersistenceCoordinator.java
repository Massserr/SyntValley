package dev.syntvalley.persistence.dirty;

import dev.syntvalley.persistence.saveddata.SyntValleySavedData;
import java.util.List;
import java.util.Objects;

/** Services bounded bookkeeping; physical canonical writes remain owned by Minecraft. */
public final class PersistenceCoordinator {
    public static final long PERIODIC_INTERVAL_TICKS = 100;
    public static final int PERIODIC_BATCH_SIZE = 64;

    private final SyntValleySavedData savedData;
    private final DirtyTracker dirtyTracker;
    private long lastPeriodicDrainGameTime;
    private long lastObservedSaveGameTime;

    public PersistenceCoordinator(SyntValleySavedData savedData, DirtyTracker dirtyTracker) {
        this.savedData = Objects.requireNonNull(savedData, "savedData");
        this.dirtyTracker = Objects.requireNonNull(dirtyTracker, "dirtyTracker");
    }

    public void onServerTick(long gameTime) {
        if (dirtyTracker.pendingCount() == 0 || gameTime < lastPeriodicDrainGameTime + PERIODIC_INTERVAL_TICKS) {
            return;
        }
        lastPeriodicDrainGameTime = gameTime;
        flushBatch(gameTime, PERIODIC_BATCH_SIZE);
    }

    public List<DirtyEntry> flushBatch(long gameTime, int maximum) {
        List<DirtyEntry> drained = dirtyTracker.drain(maximum);
        if (!drained.isEmpty()) {
            savedData.updateLastFlushGameTime(gameTime);
        }
        return drained;
    }

    public List<DirtyEntry> flushAll(long gameTime) {
        List<DirtyEntry> drained = dirtyTracker.drainAll();
        if (!drained.isEmpty()) {
            savedData.updateLastFlushGameTime(gameTime);
        }
        return drained;
    }

    public void onPostWorldSave(long gameTime) {
        lastObservedSaveGameTime = gameTime;
    }

    public int pendingCount() {
        return dirtyTracker.pendingCount();
    }

    public long lastObservedSaveGameTime() {
        return lastObservedSaveGameTime;
    }
}
