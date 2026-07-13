package dev.syntvalley.content.block;

import com.mojang.serialization.MapCodec;
import dev.syntvalley.content.blockentity.SyntCoreBlockEntity;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Physical anchor for one persistent Village. All canonical mutations are delegated server-side. */
public final class SyntCoreBlock extends BaseEntityBlock {
    public static final MapCodec<SyntCoreBlock> CODEC = simpleCodec(SyntCoreBlock::new);

    public SyntCoreBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<SyntCoreBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SyntCoreBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof SyntCoreBlockEntity core) {
            core.ensureServerBinding(serverLevel);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())
                && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof SyntCoreBlockEntity core) {
            core.handleServerRemoval(serverLevel);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult
    ) {
        if (!stack.is(Items.EMERALD)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return ItemInteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof SyntCoreBlockEntity core) {
            SyntCoreBlockEntity.HireResultStatus outcome = core.hireCitizen(serverLevel, player, stack);
            player.displayClientMessage(Component.translatable(outcome.messageKey()), false);
        } else {
            player.displayClientMessage(Component.translatable("message.syntvalley.core.unavailable"), false);
        }
        return ItemInteractionResult.SUCCESS;
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

        if (level.getBlockEntity(pos) instanceof SyntCoreBlockEntity core) {
            core.ensureServerBinding(serverLevel);
            Optional<SyntCoreBlockEntity.InspectView> view = core.inspect(serverLevel);
            if (view.isPresent()) {
                SyntCoreBlockEntity.InspectView inspection = view.orElseThrow();
                player.displayClientMessage(
                        Component.translatable(
                                "message.syntvalley.core.inspect",
                                inspection.villageId(),
                                inspection.lifecycle(),
                                inspection.revision()
                        ),
                        false
                );
                return InteractionResult.SUCCESS;
            }
        }

        player.displayClientMessage(Component.translatable("message.syntvalley.core.unavailable"), false);
        return InteractionResult.SUCCESS;
    }
}
