package dev.syntvalley.domain.citizen;

import dev.syntvalley.domain.identity.CitizenId;
import dev.syntvalley.domain.identity.VillageId;
import dev.syntvalley.domain.need.Needs;
import dev.syntvalley.domain.task.Task;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Slice 3 introduced identity/lifecycle; Slice 5 adds the deterministic living state — the citizen's
 * needs and the single active task attached directly to them. Canonical persistence authority stays
 * with the world state.
 */
public record CitizenAggregate(
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
    public CitizenAggregate {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(villageId, "villageId");
        Objects.requireNonNull(lifecycle, "lifecycle");
        Objects.requireNonNull(name, "name");
        boundEntityId = Objects.requireNonNull(boundEntityId, "boundEntityId");
        Objects.requireNonNull(needs, "needs");
        activeTask = Objects.requireNonNull(activeTask, "activeTask");

        if (revision < 1) {
            throw new IllegalArgumentException("Citizen revision must be positive");
        }
        if (bindingGeneration < 1) {
            throw new IllegalArgumentException("Binding generation must be positive");
        }
        if (!CitizenConstraints.isValidName(name)) {
            throw new IllegalArgumentException("Invalid Citizen name");
        }
        if (createdGameTime < 0) {
            throw new IllegalArgumentException("Created game time must not be negative");
        }
        if (lifecycle == CitizenLifecycle.DECEASED && boundEntityId.isPresent()) {
            throw new IllegalArgumentException("Deceased Citizen cannot retain a bound entity");
        }
        if (lifecycle == CitizenLifecycle.DECEASED && activeTask.isPresent()) {
            throw new IllegalArgumentException("Deceased Citizen cannot retain an active task");
        }
        if (activeTask.isPresent() && !activeTask.orElseThrow().citizenId().equals(id)) {
            throw new IllegalArgumentException("Active task belongs to a different Citizen");
        }
    }

    public static CitizenAggregate create(CitizenId id, VillageId villageId, String name, long gameTime) {
        return new CitizenAggregate(
                id,
                villageId,
                1,
                CitizenLifecycle.ACTIVE,
                name,
                1,
                Optional.empty(),
                gameTime,
                Needs.full(gameTime),
                Optional.empty()
        );
    }

    public CitizenEntityBinding entityBinding() {
        return new CitizenEntityBinding(id, villageId, bindingGeneration);
    }

    /** Applies a serviced simulation step (updated needs and/or active task), advancing the revision. */
    public CitizenAggregate withSimulation(Needs updatedNeeds, Optional<Task> updatedActiveTask) {
        Objects.requireNonNull(updatedNeeds, "updatedNeeds");
        Objects.requireNonNull(updatedActiveTask, "updatedActiveTask");
        if (revision == Long.MAX_VALUE) {
            throw new IllegalStateException("Citizen revision exhausted");
        }
        return new CitizenAggregate(
                id, villageId, revision + 1, lifecycle, name, bindingGeneration, boundEntityId,
                createdGameTime, updatedNeeds, updatedActiveTask);
    }

    public CitizenTransitionResult reconcileEntity(CitizenEntityBinding binding, UUID entityId) {
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(entityId, "entityId");

        if (!id.equals(binding.citizenId()) || !villageId.equals(binding.villageId())) {
            return new CitizenTransitionResult.Rejected(CitizenTransitionRejection.BINDING_CONFLICT);
        }
        if (binding.generation() != bindingGeneration) {
            return new CitizenTransitionResult.Rejected(CitizenTransitionRejection.STALE_BINDING);
        }
        if (lifecycle != CitizenLifecycle.ACTIVE) {
            return new CitizenTransitionResult.Rejected(CitizenTransitionRejection.LIFECYCLE_DISALLOWS_PRESENCE);
        }
        if (boundEntityId.isPresent()) {
            if (boundEntityId.get().equals(entityId)) {
                return new CitizenTransitionResult.Unchanged(this);
            }
            return new CitizenTransitionResult.Rejected(CitizenTransitionRejection.DUPLICATE_ENTITY);
        }
        if (revision == Long.MAX_VALUE) {
            return new CitizenTransitionResult.Rejected(CitizenTransitionRejection.REVISION_EXHAUSTED);
        }

        return new CitizenTransitionResult.Changed(new CitizenAggregate(
                id,
                villageId,
                revision + 1,
                lifecycle,
                name,
                bindingGeneration,
                Optional.of(entityId),
                createdGameTime,
                needs,
                activeTask
        ));
    }

    public CitizenTransitionResult recordDeath(UUID entityId) {
        Objects.requireNonNull(entityId, "entityId");

        if (lifecycle == CitizenLifecycle.DECEASED) {
            return new CitizenTransitionResult.Unchanged(this);
        }
        if (boundEntityId.isPresent() && !boundEntityId.get().equals(entityId)) {
            return new CitizenTransitionResult.Rejected(CitizenTransitionRejection.BINDING_CONFLICT);
        }
        if (revision == Long.MAX_VALUE) {
            return new CitizenTransitionResult.Rejected(CitizenTransitionRejection.REVISION_EXHAUSTED);
        }

        return new CitizenTransitionResult.Changed(new CitizenAggregate(
                id,
                villageId,
                revision + 1,
                CitizenLifecycle.DECEASED,
                name,
                bindingGeneration,
                Optional.empty(),
                createdGameTime,
                needs,
                Optional.empty()
        ));
    }
}
