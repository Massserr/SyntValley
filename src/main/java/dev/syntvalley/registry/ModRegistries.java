package dev.syntvalley.registry;

import dev.syntvalley.content.entity.SyntCitizenEntity;
import java.util.Objects;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * Composition point for SyntValley deferred registries.
 *
 * <p>Contains registration composition only; gameplay binding logic remains in application/content
 * adapters.</p>
 */
public final class ModRegistries {
    private ModRegistries() {
    }

    public static void register(IEventBus modEventBus) {
        Objects.requireNonNull(modEventBus, "modEventBus");
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModEntityTypes.ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(ModRegistries::addCreativeTabEntries);
        modEventBus.addListener(ModRegistries::registerEntityAttributes);
    }

    private static void addCreativeTabEntries(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(new ItemStack(ModItems.SYNT_CORE.get()));
            event.accept(new ItemStack(ModItems.VILLAGE_CONSOLE.get()));
            event.accept(new ItemStack(ModItems.VILLAGE_STORAGE.get()));
        }
    }

    private static void registerEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntityTypes.SYNT_CITIZEN.get(), SyntCitizenEntity.createAttributes().build());
    }
}
