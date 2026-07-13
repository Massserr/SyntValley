package dev.syntvalley.content.block;

import com.mojang.serialization.MapCodec;
import dev.syntvalley.content.blockentity.VillageConsoleBlockEntity;
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

/** Read-only Village window. Bound explicitly to a Village selected at a Synt Core. */
public final class VillageConsoleBlock extends BaseEntityBlock {
    public static final MapCodec<VillageConsoleBlock> CODEC = simpleCodec(VillageConsoleBlock::new);

    public VillageConsoleBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<VillageConsoleBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VillageConsoleBlockEntity(pos, state);
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

        if (level.getBlockEntity(pos) instanceof VillageConsoleBlockEntity console) {
            if (console.villageId().isEmpty()) {
                boolean linked = console.tryLink(serverLevel, player);
                player.displayClientMessage(
                        Component.translatable(linked
                                ? "message.syntvalley.console.linked"
                                : "message.syntvalley.console.no_selection"),
                        false
                );
                return InteractionResult.SUCCESS;
            }
            if (player instanceof ServerPlayer serverPlayer && console.openOverviewFor(serverLevel, serverPlayer)) {
                return InteractionResult.SUCCESS;
            }
        }

        player.displayClientMessage(Component.translatable("message.syntvalley.core.unavailable"), false);
        return InteractionResult.SUCCESS;
    }
}
