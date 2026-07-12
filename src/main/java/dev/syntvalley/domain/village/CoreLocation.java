package dev.syntvalley.domain.village;

import java.util.Objects;

/** Minecraft-neutral location value; packedPos follows BlockPos#asLong semantics. */
public record CoreLocation(String dimensionId, long packedPos) {
    public CoreLocation {
        Objects.requireNonNull(dimensionId, "dimensionId");
        if (!VillageConstraints.isValidDimensionId(dimensionId)) {
            throw new IllegalArgumentException("Invalid dimension id");
        }
    }
}
