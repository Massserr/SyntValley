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

    /** Builds a placement with a stable plan hash derived from the template identity and the site. */
    public static SitePlacement of(
            String dimensionId, int originX, int originY, int originZ, Rotation rotation,
            BuildingTemplateId templateId, int templateVersion) {
        return new SitePlacement(dimensionId, originX, originY, originZ, rotation,
                planHash(dimensionId, originX, originY, originZ, rotation, templateId, templateVersion));
    }

    /**
     * Deterministic hash of the whole plan (template id + version + dimension + origin + rotation). The
     * runtime recomputes it to detect a template that changed shape or a tampered plan before it keeps
     * placing blocks. Uses only String/enum/int hashing, so it is stable across servers and restarts.
     */
    public static long planHash(
            String dimensionId, int originX, int originY, int originZ, Rotation rotation,
            BuildingTemplateId templateId, int templateVersion) {
        long hash = 1125899906842597L;
        hash = 31 * hash + templateId.value().hashCode();
        hash = 31 * hash + templateVersion;
        hash = 31 * hash + dimensionId.hashCode();
        hash = 31 * hash + originX;
        hash = 31 * hash + originY;
        hash = 31 * hash + originZ;
        hash = 31 * hash + rotation.ordinal();
        return hash;
    }
}
