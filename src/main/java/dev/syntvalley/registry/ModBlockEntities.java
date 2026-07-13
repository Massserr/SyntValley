package dev.syntvalley.registry;

import dev.syntvalley.bootstrap.ProjectIdentity;
import dev.syntvalley.content.blockentity.SyntCoreBlockEntity;
import dev.syntvalley.content.blockentity.VillageConsoleBlockEntity;
import dev.syntvalley.content.blockentity.VillageStorageBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ProjectIdentity.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SyntCoreBlockEntity>> SYNT_CORE =
            BLOCK_ENTITY_TYPES.register(
                    "synt_core",
                    () -> BlockEntityType.Builder.of(SyntCoreBlockEntity::new, ModBlocks.SYNT_CORE.get()).build(null)
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VillageConsoleBlockEntity>> VILLAGE_CONSOLE =
            BLOCK_ENTITY_TYPES.register(
                    "village_console",
                    () -> BlockEntityType.Builder.of(VillageConsoleBlockEntity::new, ModBlocks.VILLAGE_CONSOLE.get())
                            .build(null)
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VillageStorageBlockEntity>> VILLAGE_STORAGE =
            BLOCK_ENTITY_TYPES.register(
                    "village_storage",
                    () -> BlockEntityType.Builder.of(VillageStorageBlockEntity::new, ModBlocks.VILLAGE_STORAGE.get())
                            .build(null)
            );

    private ModBlockEntities() {
    }
}
