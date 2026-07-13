package dev.syntvalley.content.blockentity;

import dev.syntvalley.application.query.VillageOverviewDto;
import dev.syntvalley.bootstrap.ServerRuntimeManager;
import dev.syntvalley.bootstrap.SyntValleyServerRuntime;
import dev.syntvalley.domain.identity.VillageId;
import dev.syntvalley.network.VillageOverviewSnapshotPayload;
import dev.syntvalley.registry.ModBlockEntities;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

/** Read-only Village window. Stores only the bound VillageId; canonical data stays in world state. */
public final class VillageConsoleBlockEntity extends BlockEntity {
    private static final String TAG_VILLAGE_ID = "village_id";

    private VillageId villageId;

    public VillageConsoleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VILLAGE_CONSOLE.get(), pos, state);
    }

    public Optional<VillageId> villageId() {
        return Optional.ofNullable(villageId);
    }

    /** Consumes the player's pending Core selection and binds this Console to it. */
    public boolean tryLink(ServerLevel level, Player player) {
        SyntValleyServerRuntime runtime = ServerRuntimeManager.getOrCreate(level.getServer());
        Optional<VillageId> bound = runtime.bindConsole(player.getUUID(), level.getGameTime());
        if (bound.isEmpty()) {
            return false;
        }
        villageId = bound.orElseThrow();
        setChanged();
        return true;
    }

    /** Opens a server-registered overview session and pushes the initial snapshot to the viewer. */
    public boolean openOverviewFor(ServerLevel level, ServerPlayer player) {
        if (villageId == null) {
            return false;
        }
        SyntValleyServerRuntime runtime = ServerRuntimeManager.getOrCreate(level.getServer());
        Optional<VillageOverviewDto> overview = runtime.openOverview(
                player.getUUID(),
                villageId,
                level.dimension().location().toString(),
                worldPosition.asLong(),
                level.getGameTime());
        if (overview.isEmpty()) {
            return false;
        }
        PacketDistributor.sendToPlayer(player, new VillageOverviewSnapshotPayload(overview.orElseThrow()));
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (villageId != null) {
            tag.putUUID(TAG_VILLAGE_ID, villageId.value());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        villageId = tag.hasUUID(TAG_VILLAGE_ID) ? new VillageId(tag.getUUID(TAG_VILLAGE_ID)) : null;
    }
}
