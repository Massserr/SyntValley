package dev.syntvalley.persistence.saveddata;

import dev.syntvalley.domain.decision.DecisionLog;
import dev.syntvalley.domain.decision.DecisionRecord;
import dev.syntvalley.domain.identity.VillageId;
import dev.syntvalley.domain.memory.MemoryRecord;
import dev.syntvalley.domain.memory.MemoryStore;
import dev.syntvalley.persistence.dirty.DirtyKey;
import dev.syntvalley.persistence.dirty.DirtyReason;
import dev.syntvalley.persistence.dirty.DirtyTracker;
import java.util.List;
import java.util.Objects;
import net.minecraft.server.MinecraftServer;

/**
 * Server-thread adapter that hydrates a village's memory store / decision log from SavedData and writes
 * the whole (small, bounded) collection back after a mutation. Memories and decisions are canonical
 * state: they survive restarts, while runtime caches are rebuilt from here.
 */
public final class SavedDataVillageLogRepository {
    private final MinecraftServer server;
    private final SyntValleySavedData savedData;
    private final DirtyTracker dirtyTracker;

    public SavedDataVillageLogRepository(
            MinecraftServer server, SyntValleySavedData savedData, DirtyTracker dirtyTracker) {
        this.server = Objects.requireNonNull(server, "server");
        this.savedData = Objects.requireNonNull(savedData, "savedData");
        this.dirtyTracker = Objects.requireNonNull(dirtyTracker, "dirtyTracker");
    }

    /** Rebuilds the village's memory store from the persisted records (empty when none or unavailable). */
    public MemoryStore loadMemories(VillageId villageId) {
        assertServerThread();
        Objects.requireNonNull(villageId, "villageId");
        MemoryStore store = new MemoryStore(
                PersistenceBounds.MAX_MEMORIES_PER_VILLAGE, PersistenceBounds.MAX_PINNED_MEMORIES_PER_VILLAGE);
        if (savedData.isAvailable()) {
            List<MemoryRecord> records = savedData.stateSnapshot().memories().get(villageId);
            if (records != null) {
                records.forEach(store::add);
            }
        }
        return store;
    }

    /** Rebuilds the village's decision log; sequences continue after the highest persisted one. */
    public DecisionLog loadDecisions(VillageId villageId) {
        assertServerThread();
        Objects.requireNonNull(villageId, "villageId");
        List<DecisionRecord> records = savedData.isAvailable()
                ? savedData.stateSnapshot().decisions().getOrDefault(villageId, List.of())
                : List.of();
        return DecisionLog.restore(PersistenceBounds.MAX_DECISIONS_PER_VILLAGE, records);
    }

    public boolean saveMemories(VillageId villageId, List<MemoryRecord> records) {
        assertServerThread();
        Objects.requireNonNull(villageId, "villageId");
        Objects.requireNonNull(records, "records");
        if (!savedData.isAvailable()) {
            return false;
        }
        WorldState current = savedData.stateSnapshot();
        if (!current.villages().containsKey(villageId)) {
            return false;
        }
        DirtyKey dirtyKey = DirtyKey.memory(villageId);
        long nextDataRevision = nextDataRevision(current);
        if (!dirtyTracker.canAccept(dirtyKey) || nextDataRevision < 0) {
            return false;
        }
        savedData.replaceState(current.withVillageMemories(villageId, records, nextDataRevision));
        dirtyTracker.mark(dirtyKey, DirtyReason.UPDATED);
        return true;
    }

    public boolean saveDecisions(VillageId villageId, List<DecisionRecord> records) {
        assertServerThread();
        Objects.requireNonNull(villageId, "villageId");
        Objects.requireNonNull(records, "records");
        if (!savedData.isAvailable()) {
            return false;
        }
        WorldState current = savedData.stateSnapshot();
        if (!current.villages().containsKey(villageId)) {
            return false;
        }
        DirtyKey dirtyKey = DirtyKey.decision(villageId);
        long nextDataRevision = nextDataRevision(current);
        if (!dirtyTracker.canAccept(dirtyKey) || nextDataRevision < 0) {
            return false;
        }
        savedData.replaceState(current.withVillageDecisions(villageId, records, nextDataRevision));
        dirtyTracker.mark(dirtyKey, DirtyReason.UPDATED);
        return true;
    }

    private long nextDataRevision(WorldState current) {
        return current.dataRevision() == Long.MAX_VALUE ? -1 : current.dataRevision() + 1;
    }

    private void assertServerThread() {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Village log repository may only be accessed on the logical server thread");
        }
    }
}
