package dev.syntvalley.persistence.saveddata;

import dev.syntvalley.domain.identity.VillageId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable canonical in-memory representation of schema 1 implemented by Slice 2. */
public record WorldState(
        UUID worldInstanceId,
        long dataRevision,
        long createdAtEpochMs,
        long lastFlushGameTime,
        Map<VillageId, VillagePersistentRecord> villages
) {
    public WorldState {
        Objects.requireNonNull(worldInstanceId, "worldInstanceId");
        if (dataRevision < 0 || createdAtEpochMs < 0 || lastFlushGameTime < 0) {
            throw new IllegalArgumentException("World state revisions and times must not be negative");
        }
        Objects.requireNonNull(villages, "villages");
        if (villages.size() > PersistenceBounds.MAX_VILLAGES) {
            throw new IllegalArgumentException("Village collection exceeds schema bound");
        }

        LinkedHashMap<VillageId, VillagePersistentRecord> copy = new LinkedHashMap<>();
        villages.forEach((id, record) -> {
            Objects.requireNonNull(id, "Village map key");
            Objects.requireNonNull(record, "Village map value");
            if (!id.equals(record.id())) {
                throw new IllegalArgumentException("Village map key does not match record ID");
            }
            copy.put(id, record);
        });
        villages = Collections.unmodifiableMap(copy);
    }

    public static WorldState createNew(long epochMs) {
        return new WorldState(UUID.randomUUID(), 0, epochMs, 0, Map.of());
    }

    public WorldState withVillage(VillagePersistentRecord village, long nextDataRevision) {
        Objects.requireNonNull(village, "village");
        LinkedHashMap<VillageId, VillagePersistentRecord> updated = new LinkedHashMap<>(villages);
        updated.put(village.id(), village);
        return new WorldState(worldInstanceId, nextDataRevision, createdAtEpochMs, lastFlushGameTime, updated);
    }

    public WorldState withLastFlushGameTime(long gameTime) {
        return new WorldState(worldInstanceId, dataRevision, createdAtEpochMs, gameTime, villages);
    }
}
