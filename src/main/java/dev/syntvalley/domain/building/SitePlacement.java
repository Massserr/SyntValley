package dev.syntvalley.domain.building;

import java.util.Objects;

/**
 * An immutable, validated placement of a template in the world: the origin block the template's local
 * offsets are anchored to, the rotation, and a stable plan hash. The hash lets the runtime detect a
 * template that changed shape (or a plan that was tampered with) before it keeps placing blocks. The
 * coordinates are plain ints so the domain stays Minecraft-free; the adapter maps them to a BlockPos.
 */
public record SitePlacement(
        String dimensionId, int originX, int originY, int originZ, Rotation rotation, long planHash) {
    public SitePlacement {
        Objects.requireNonNull(dimensionId, "dimensionId");
        Objects.requireNonNull(rotation, "rotation");
        if (dimensionId.isBlank()) {
            throw new IllegalArgumentException("dimensionId must not be blank");
        }
    }
}
