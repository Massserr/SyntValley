package dev.syntvalley.registry;

import dev.syntvalley.bootstrap.ProjectIdentity;
import dev.syntvalley.content.block.SyntCoreBlock;
import dev.syntvalley.content.block.VillageConsoleBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ProjectIdentity.MOD_ID);

    public static final DeferredBlock<SyntCoreBlock> SYNT_CORE = BLOCKS.registerBlock(
            "synt_core",
            SyntCoreBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(3.5F, 6.0F)
                    .sound(SoundType.AMETHYST)
                    .pushReaction(PushReaction.BLOCK)
    );

    public static final DeferredBlock<VillageConsoleBlock> VILLAGE_CONSOLE = BLOCKS.registerBlock(
            "village_console",
            VillageConsoleBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .strength(3.5F, 6.0F)
                    .sound(SoundType.AMETHYST)
                    .pushReaction(PushReaction.BLOCK)
    );

    private ModBlocks() {
    }
}
