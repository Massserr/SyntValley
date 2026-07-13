package dev.syntvalley.registry;

import dev.syntvalley.bootstrap.ProjectIdentity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ProjectIdentity.MOD_ID);

    public static final DeferredItem<BlockItem> SYNT_CORE = ITEMS.registerSimpleBlockItem(
            ModBlocks.SYNT_CORE,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> VILLAGE_CONSOLE = ITEMS.registerSimpleBlockItem(
            ModBlocks.VILLAGE_CONSOLE,
            new Item.Properties()
    );

    public static final DeferredItem<BlockItem> VILLAGE_STORAGE = ITEMS.registerSimpleBlockItem(
            ModBlocks.VILLAGE_STORAGE,
            new Item.Properties()
    );

    private ModItems() {
    }
}
