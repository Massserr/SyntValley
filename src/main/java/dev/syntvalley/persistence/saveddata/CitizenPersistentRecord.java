package dev.syntvalley.persistence.saveddata;

import dev.syntvalley.domain.citizen.CitizenAggregate;
import dev.syntvalley.domain.citizen.CitizenLifecycle;
import dev.syntvalley.domain.identity.CitizenId;
import dev.syntvalley.domain.identity.VillageId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Schema-1 Citizen record added in Slice 3. It currently mirrors the aggregate one-to-one; future
 * persistent-only fields (profession, needs, mood, memory refs) attach here without moving authority.
 */
public record CitizenPersistentRecord(
        CitizenId id,
        VillageId villageId,
        long revision,
        CitizenLifecycle lifecycle,
        String name,
        int bindingGeneration,
        Optional<UUID> boundEntityId,
        long createdGameTime
) {
    public CitizenPersistentRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(villageId, "villageId");
        Objects.requireNonNull(lifecycle, "lifecycle");
        Objects.requireNonNull(name, "name");
        boundEntityId = Objects.requireNonNull(boundEntityId, "boundEntityId");

        // Reuse domain validation for identity, lifecycle, name, generation, revision and times.
        new CitizenAggregate(id, villageId, revision, lifecycle, name, bindingGeneration, boundEntityId, createdGameTime);
    }

    public static CitizenPersistentRecord fromAggregate(CitizenAggregate citizen) {
        Objects.requireNonNull(citizen, "citizen");
        return new CitizenPersistentRecord(
                citizen.id(),
                citizen.villageId(),
                citizen.revision(),
                citizen.lifecycle(),
                citizen.name(),
                citizen.bindingGeneration(),
                citizen.boundEntityId(),
                citizen.createdGameTime()
        );
    }

    public CitizenPersistentRecord withAggregate(CitizenAggregate citizen) {
        Objects.requireNonNull(citizen, "citizen");
        if (!id.equals(citizen.id())) {
            throw new IllegalArgumentException("Cannot replace a Citizen record with another ID");
        }
        return fromAggregate(citizen);
    }

    public CitizenAggregate toAggregate() {
        return new CitizenAggregate(id, villageId, revision, lifecycle, name, bindingGeneration, boundEntityId, createdGameTime);
    }
}
