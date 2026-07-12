package dev.syntvalley.persistence.saveddata;

import dev.syntvalley.domain.identity.VillageId;
import dev.syntvalley.domain.village.CoreLocation;
import dev.syntvalley.domain.village.VillageAggregate;
import dev.syntvalley.domain.village.VillageLifecycle;
import java.util.Objects;
import java.util.Optional;

/** Schema-1 Village record. Stable defaults are preserved even before their gameplay slices exist. */
public record VillagePersistentRecord(
        VillageId id,
        long revision,
        String name,
        VillageLifecycle lifecycle,
        Optional<CoreLocation> coreLocation,
        int lastBindingGeneration,
        long createdGameTime,
        long lastSimulatedGameTime,
        VillagePrioritiesRecord priorities,
        VillagePolicyRecord policy
) {
    public VillagePersistentRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(lifecycle, "lifecycle");
        coreLocation = Objects.requireNonNull(coreLocation, "coreLocation");
        Objects.requireNonNull(priorities, "priorities");
        Objects.requireNonNull(policy, "policy");

        // Reuse domain validation for identity, lifecycle, name, location, revision and times.
        new VillageAggregate(
                id,
                revision,
                name,
                lifecycle,
                coreLocation,
                lastBindingGeneration,
                createdGameTime,
                lastSimulatedGameTime
        );
    }

    public static VillagePersistentRecord fromNewAggregate(VillageAggregate village) {
        Objects.requireNonNull(village, "village");
        return new VillagePersistentRecord(
                village.id(),
                village.revision(),
                village.name(),
                village.lifecycle(),
                village.coreLocation(),
                village.lastBindingGeneration(),
                village.createdGameTime(),
                village.lastSimulatedGameTime(),
                VillagePrioritiesRecord.defaults(),
                VillagePolicyRecord.defaults()
        );
    }

    public VillagePersistentRecord withAggregate(VillageAggregate village) {
        Objects.requireNonNull(village, "village");
        if (!id.equals(village.id())) {
            throw new IllegalArgumentException("Cannot replace a Village record with another ID");
        }
        return new VillagePersistentRecord(
                village.id(),
                village.revision(),
                village.name(),
                village.lifecycle(),
                village.coreLocation(),
                village.lastBindingGeneration(),
                village.createdGameTime(),
                village.lastSimulatedGameTime(),
                priorities,
                policy
        );
    }

    public VillageAggregate toAggregate() {
        return new VillageAggregate(
                id,
                revision,
                name,
                lifecycle,
                coreLocation,
                lastBindingGeneration,
                createdGameTime,
                lastSimulatedGameTime
        );
    }
}
