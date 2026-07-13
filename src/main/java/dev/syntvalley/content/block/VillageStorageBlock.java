package dev.syntvalley.content.block;

import com.mojang.serialization.MapCodec;
import dev.syntvalley.content.blockentity.VillageStorageBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Village-owned storage. Linked to a Village explicitly, then opened like a chest. */
public final class VillageStorageBlock extends BaseEntityBlock {
    public static final MapCodec<VillageStorageBlock> CODEC = simpleCodec(VillageStorageBlock::new);

    public VillageStorageBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<VillageStorageBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VillageStorageBlockEntity(pos, state);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())
                && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof VillageStorageBlockEntity storage) {
            storage.dropContents(serverLevel);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        if (level.getBlockEntity(pos) instanceof VillageStorageBlockEntity storage) {
            if (storage.villageId().isEmpty()) {
                boolean linked = storage.tryLink(serverLevel, player);
                player.displayClientMessage(
                        Component.translatable(linked
                                ? "message.syntvalley.storage.linked"
                                : "message.syntvalley.console.no_selection"),
                        false
                );
                return InteractionResult.SUCCESS;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                storage.openStorage(serverPlayer);
                return InteractionResult.SUCCESS;
            }
        }

        player.displayClientMessage(Component.translatable("message.syntvalley.core.unavailable"), false);
        return InteractionResult.SUCCESS;
    }
}
