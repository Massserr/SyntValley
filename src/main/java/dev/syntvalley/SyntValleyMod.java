package dev.syntvalley;

import dev.syntvalley.bootstrap.ProjectIdentity;
import dev.syntvalley.config.SyntValleyConfig;
import dev.syntvalley.observability.SyntValleyLog;
import dev.syntvalley.registry.ModRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Physical-common bootstrap. It must never own mutable world state.
 */
@Mod(SyntValleyMod.MOD_ID)
public final class SyntValleyMod {
    public static final String MOD_ID = ProjectIdentity.MOD_ID;

    public SyntValleyMod(IEventBus modEventBus, ModContainer modContainer) {
        ModRegistries.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.COMMON, SyntValleyConfig.COMMON_SPEC);
        modEventBus.addListener(SyntValleyMod::onCommonSetup);

        SyntValleyLog.logger().info("Initializing {} ({})", ProjectIdentity.DISPLAY_NAME, MOD_ID);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        if (SyntValleyConfig.STARTUP_DIAGNOSTICS.getAsBoolean()) {
            SyntValleyLog.logger().info(
                    "SyntValley common setup: Java {} at {}, Minecraft target {}",
                    System.getProperty("java.version"),
                    System.getProperty("java.home"),
                    ProjectIdentity.MINECRAFT_VERSION
            );
        }
    }
}
