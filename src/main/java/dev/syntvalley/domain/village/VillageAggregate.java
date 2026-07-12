package dev.syntvalley.domain.village;

import dev.syntvalley.domain.identity.VillageId;
import java.util.Objects;
import java.util.Optional;

/** Minimal Slice 2 aggregate. Later slices extend it without moving persistence authority. */
public record VillageAggregate(
        VillageId id,
        long revision,
        String name,
        VillageLifecycle lifecycle,
        Optional<CoreLocation> coreLocation,
        int lastBindingGeneration,
        long createdGameTime,
        long lastSimulatedGameTime
) {
    public VillageAggregate {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(lifecycle, "lifecycle");
        coreLocation = Objects.requireNonNull(coreLocation, "coreLocation");

        if (revision < 1) {
            throw new IllegalArgumentException("Village revision must be positive");
        }
        if (!VillageConstraints.isValidName(name)) {
            throw new IllegalArgumentException("Invalid Village name");
        }
        if (lastBindingGeneration < 1) {
            throw new IllegalArgumentException("Last binding generation must be positive");
        }
        if (createdGameTime < 0 || lastSimulatedGameTime < 0) {
            throw new IllegalArgumentException("Game times must not be negative");
        }
        if (lifecycle == VillageLifecycle.ACTIVE && coreLocation.isEmpty()) {
            throw new IllegalArgumentException("Active Village requires a Core location");
        }
        if (lifecycle == VillageLifecycle.ORPHANED && coreLocation.isPresent()) {
            throw new IllegalArgumentException("Orphaned Village cannot have an active Core location");
        }
    }

    public static VillageAggregate create(VillageId id, String name, CoreLocation coreLocation, long gameTime) {
        return new VillageAggregate(
                id,
                1,
                name,
                VillageLifecycle.ACTIVE,
                Optional.of(coreLocation),
                1,
                gameTime,
                gameTime
        );
    }

    public CoreBinding coreBinding() {
        return new CoreBinding(id, lastBindingGeneration);
    }

    public VillageTransitionResult reconcileCore(CoreBinding localBinding, CoreLocation observedLocation) {
        Objects.requireNonNull(localBinding, "localBinding");
        Objects.requireNonNull(observedLocation, "observedLocation");

        if (!id.equals(localBinding.villageId())) {
            return new VillageTransitionResult.Rejected(VillageTransitionRejection.BINDING_CONFLICT);
        }

        if (lifecycle == VillageLifecycle.ACTIVE) {
            if (lastBindingGeneration == localBinding.generation()
                    && coreLocation.filter(observedLocation::equals).isPresent()) {
                return new VillageTransitionResult.Unchanged(this);
            }
            return new VillageTransitionResult.Rejected(VillageTransitionRejection.BINDING_CONFLICT);
        }

        if (lifecycle != VillageLifecycle.ORPHANED) {
            return new VillageTransitionResult.Rejected(VillageTransitionRejection.LIFECYCLE_DISALLOWS_BINDING);
        }
        if (localBinding.generation() != lastBindingGeneration) {
            return new VillageTransitionResult.Rejected(VillageTransitionRejection.STALE_BINDING);
        }
        if (lastBindingGeneration == Integer.MAX_VALUE) {
            return new VillageTransitionResult.Rejected(VillageTransitionRejection.GENERATION_EXHAUSTED);
        }
        if (revision == Long.MAX_VALUE) {
            return new VillageTransitionResult.Rejected(VillageTransitionRejection.REVISION_EXHAUSTED);
        }

        return new VillageTransitionResult.Changed(new VillageAggregate(
                id,
                revision + 1,
                name,
                VillageLifecycle.ACTIVE,
                Optional.of(observedLocation),
                lastBindingGeneration + 1,
                createdGameTime,
                lastSimulatedGameTime
        ));
    }

    public VillageTransitionResult orphanCore(CoreBinding removedBinding, CoreLocation removedLocation) {
        Objects.requireNonNull(removedBinding, "removedBinding");
        Objects.requireNonNull(removedLocation, "removedLocation");

        if (!id.equals(removedBinding.villageId())) {
            return new VillageTransitionResult.Rejected(VillageTransitionRejection.BINDING_CONFLICT);
        }
        if (lifecycle == VillageLifecycle.ORPHANED) {
            if (removedBinding.generation() <= lastBindingGeneration) {
                return new VillageTransitionResult.Unchanged(this);
            }
            return new VillageTransitionResult.Rejected(VillageTransitionRejection.STALE_BINDING);
        }
        if (lifecycle != VillageLifecycle.ACTIVE
                || removedBinding.generation() != lastBindingGeneration
                || coreLocation.filter(removedLocation::equals).isEmpty()) {
            return new VillageTransitionResult.Rejected(VillageTransitionRejection.BINDING_CONFLICT);
        }
        if (revision == Long.MAX_VALUE) {
            return new VillageTransitionResult.Rejected(VillageTransitionRejection.REVISION_EXHAUSTED);
        }

        return new VillageTransitionResult.Changed(new VillageAggregate(
                id,
                revision + 1,
                name,
                VillageLifecycle.ORPHANED,
                Optional.empty(),
                lastBindingGeneration,
                createdGameTime,
                lastSimulatedGameTime
        ));
    }
}
