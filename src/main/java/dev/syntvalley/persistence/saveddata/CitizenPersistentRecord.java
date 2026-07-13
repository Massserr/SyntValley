package dev.syntvalley.persistence.saveddata;

import dev.syntvalley.domain.citizen.CitizenAggregate;
import dev.syntvalley.domain.citizen.CitizenLifecycle;
import dev.syntvalley.domain.identity.CitizenId;
import dev.syntvalley.domain.identity.VillageId;
import dev.syntvalley.domain.need.Needs;
import dev.syntvalley.domain.task.Task;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Schema-1 Citizen record. Slice 3 stored identity; Slice 5 adds needs and the single active task.
 * Older saves without these fields default to full needs and no task on load (forward fill).
 */
public record CitizenPersistentRecord(
        CitizenId id,
        VillageId villageId,
        long revision,
        CitizenLifecycle lifecycle,
        String name,
        int bindingGeneration,
        Optional<UUID> boundEntityId,
        long createdGameTime,
        Needs needs,
        Optional<Task> activeTask
) {
    public CitizenPersistentRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(villageId, "villageId");
        Objects.requireNonNull(lifecycle, "lifecycle");
        Objects.requireNonNull(name, "name");
        boundEntityId = Objects.requireNonNull(boundEntityId, "boundEntityId");
        Objects.requireNonNull(needs, "needs");
        activeTask = Objects.requireNonNull(activeTask, "activeTask");

        // Reuse domain validation for the full aggregate shape.
        new CitizenAggregate(
                id, villageId, revision, lifecycle, name, bindingGeneration, boundEntityId,
                createdGameTime, needs, activeTask);
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
                citizen.createdGameTime(),
                citizen.needs(),
                citizen.activeTask()
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
        return new CitizenAggregate(
                id, villageId, revision, lifecycle, name, bindingGeneration, boundEntityId,
                createdGameTime, needs, activeTask);
    }
}
