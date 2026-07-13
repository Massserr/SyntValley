package dev.syntvalley.content.blockentity;

import dev.syntvalley.bootstrap.ServerRuntimeManager;
import dev.syntvalley.bootstrap.SyntValleyServerRuntime;
import dev.syntvalley.domain.identity.VillageId;
import dev.syntvalley.domain.village.VillageAggregate;
import dev.syntvalley.registry.ModBlockEntities;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

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

    public Optional<InspectView> inspect(ServerLevel level) {
        if (villageId == null) {
            return Optional.empty();
        }
        return ServerRuntimeManager.getOrCreate(level.getServer())
                .inspectVillage(villageId)
                .map(InspectView::fromVillage);
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

    public record InspectView(String villageId, String lifecycle, long revision) {
        private static InspectView fromVillage(VillageAggregate village) {
            return new InspectView(village.id().toString(), village.lifecycle().name(), village.revision());
        }
    }
}
