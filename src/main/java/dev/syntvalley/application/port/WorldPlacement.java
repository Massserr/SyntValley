package dev.syntvalley.application.port;

import dev.syntvalley.domain.resource.ResourceKey;

/**
 * Minecraft-free query + placement surface over the world for build projects. The adapter enforces the
 * conservative rules — loaded chunks, world border/build height, replaceable/clearance, core/console
 * exclusion, and a protection hook — so the planner and service only ask yes/no questions and request
 * single-block placements. Every placement is re-checked with {@link #isBuildable} immediately before it
 * happens, so nothing overwrites an unexpected block.
 */
public interface WorldPlacement {
    /** Can a project block be placed at this absolute position right now (clear, loaded, in-bounds, allowed)? */
    boolean isBuildable(String dimensionId, int x, int y, int z);

    /** Is the block at this position solid enough to support a foundation resting on top of it? */
    boolean isSolidGround(String dimensionId, int x, int y, int z);

    /** Places the resource as a block at the position; returns whether it was actually placed. */
    boolean placeBlock(String dimensionId, int x, int y, int z, ResourceKey block);
}
