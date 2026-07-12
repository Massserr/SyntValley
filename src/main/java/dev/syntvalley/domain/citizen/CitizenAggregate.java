package dev.syntvalley.domain.citizen;

import dev.syntvalley.domain.identity.CitizenId;
import dev.syntvalley.domain.identity.VillageId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Minimal Slice 3 Citizen aggregate. Canonical identity, village ownership and lifecycle live here;
 * the Minecraft entity is only a presence of this record. Later slices add profession, needs, mood,
 * memory and task refs without moving persistence authority.
 */
public record CitizenAggregate(
        CitizenId id,
        VillageId villageId,
        long revision,
        CitizenLifecycle lifecycle,
        String name,
        int bindingGeneration,
        Optional<UUID> boundEntityId,
        long createdGameTime
) {
    public CitizenAggregate {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(villageId, "villageId");
        Objects.requireNonNull(lifecycle, "lifecycle");
        Objects.requireNonNull(name, "name");
        boundEntityId = Objects.requireNonNull(boundEntityId, "boundEntityId");

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
                gameTime
        );
    }

    public CitizenEntityBinding entityBinding() {
        return new CitizenEntityBinding(id, villageId, bindingGeneration);
    }

    /**
     * Reconciles a loaded/spawned entity against this record. The first entity to reconcile claims
     * presence; a different entity presenting the same identity is a duplicate and is rejected so it
     * can be quarantined instead of splitting the identity.
     */
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
                createdGameTime
        ));
    }

    /**
     * Records the death of the bound entity. Death does not auto-respawn or delete the record; the
     * Citizen becomes DECEASED so a later hire never accidentally revives this identity.
     */
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
                createdGameTime
        ));
    }
}
