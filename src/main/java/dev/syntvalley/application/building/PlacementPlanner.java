package dev.syntvalley.application.building;

import dev.syntvalley.application.port.WorldPlacement;
import dev.syntvalley.domain.building.BlockOffset;
import dev.syntvalley.domain.building.BuildingTemplate;
import dev.syntvalley.domain.building.Rotation;
import dev.syntvalley.domain.building.SitePlacement;
import dev.syntvalley.domain.building.TemplateBlock;
import java.util.Objects;
import java.util.Optional;

/**
 * Deterministic, bounded search for a valid site for a template within a village. Candidates sit at the
 * core's Y level, in rings of increasing distance from the core, each tried in four rotations; the first
 * site whose every block is buildable and whose foundation rests on solid ground wins. Java picks the
 * spot — never the player or free-form input — and the search is finite so it stays within a tick budget.
 */
public final class PlacementPlanner {
    private final int minRingOffset;
    private final int searchRadius;

    public PlacementPlanner(int minRingOffset, int searchRadius) {
        if (minRingOffset < 1 || searchRadius < minRingOffset) {
            throw new IllegalArgumentException("invalid search bounds");
        }
        this.minRingOffset = minRingOffset;
        this.searchRadius = searchRadius;
    }

    public static PlacementPlanner defaults() {
        return new PlacementPlanner(2, 6);
    }

    public Optional<SitePlacement> plan(
            String dimensionId, int coreX, int coreY, int coreZ, BuildingTemplate template, WorldPlacement world) {
        Objects.requireNonNull(dimensionId, "dimensionId");
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(world, "world");
        for (int ring = minRingOffset; ring <= searchRadius; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue; // only the outer ring at this radius, so each candidate is visited once
                    }
                    for (Rotation rotation : Rotation.values()) {
                        if (siteValid(dimensionId, coreX + dx, coreY, coreZ + dz, rotation, template, world)) {
                            return Optional.of(SitePlacement.of(
                                    dimensionId, coreX + dx, coreY, coreZ + dz, rotation,
                                    template.id(), template.version()));
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private boolean siteValid(
            String dimensionId, int originX, int originY, int originZ, Rotation rotation,
            BuildingTemplate template, WorldPlacement world) {
        int minY = Integer.MAX_VALUE;
        for (TemplateBlock block : template.blocks()) {
            minY = Math.min(minY, rotation.apply(block.offset()).y());
        }
        for (TemplateBlock block : template.blocks()) {
            BlockOffset rotated = rotation.apply(block.offset());
            int x = originX + rotated.x();
            int y = originY + rotated.y();
            int z = originZ + rotated.z();
            if (!world.isBuildable(dimensionId, x, y, z)) {
                return false;
            }
            if (rotated.y() == minY && !world.isSolidGround(dimensionId, x, y - 1, z)) {
                return false;
            }
        }
        return true;
    }
}
