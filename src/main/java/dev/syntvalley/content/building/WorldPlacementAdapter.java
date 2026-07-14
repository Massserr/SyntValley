package dev.syntvalley.content.building;

import dev.syntvalley.application.port.WorldPlacement;
import dev.syntvalley.domain.resource.ResourceKey;
import dev.syntvalley.registry.ModBlocks;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The conservative default {@link WorldPlacement} adapter over a single ServerLevel. It only ever builds
 * into replaceable space (air/plants) inside loaded, in-bounds chunks, so it never overwrites a player's
 * blocks, and it refuses to touch the mod's own control blocks. Placement resolves the resource id to a
 * vanilla block; an unknown id is refused rather than guessed. Every project block is re-checked with
 * {@link #isBuildable} immediately before placement by the service.
 */
public final class WorldPlacementAdapter implements WorldPlacement {
    private final ServerLevel level;
    private final String dimensionId;

    public WorldPlacementAdapter(ServerLevel level) {
        this.level = Objects.requireNonNull(level, "level");
        this.dimensionId = level.dimension().location().toString();
    }

    @Override
    public boolean isBuildable(String dimensionId, int x, int y, int z) {
        if (!this.dimensionId.equals(dimensionId)) {
            return false;
        }
        if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
            return false;
        }
        BlockPos pos = new BlockPos(x, y, z);
        if (!level.isLoaded(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.canBeReplaced()) {
            return false;
        }
        return !state.is(ModBlocks.SYNT_CORE.get())
                && !state.is(ModBlocks.VILLAGE_CONSOLE.get())
                && !state.is(ModBlocks.VILLAGE_STORAGE.get());
    }

    @Override
    public boolean isSolidGround(String dimensionId, int x, int y, int z) {
        if (!this.dimensionId.equals(dimensionId)) {
            return false;
        }
        if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
            return false;
        }
        BlockPos pos = new BlockPos(x, y, z);
        if (!level.isLoaded(pos)) {
            return false;
        }
        return level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP);
    }

    @Override
    public boolean placeBlock(String dimensionId, int x, int y, int z, ResourceKey block) {
        if (!this.dimensionId.equals(dimensionId)) {
            return false;
        }
        ResourceLocation id = ResourceLocation.tryParse(block.value());
        if (id == null) {
            return false;
        }
        Optional<Block> resolved = BuiltInRegistries.BLOCK.getOptional(id);
        if (resolved.isEmpty()) {
            return false; // unknown block id — refuse to guess
        }
        return level.setBlockAndUpdate(new BlockPos(x, y, z), resolved.orElseThrow().defaultBlockState());
    }
}
