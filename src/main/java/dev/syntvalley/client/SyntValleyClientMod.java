package dev.syntvalley.client;

import dev.syntvalley.SyntValleyMod;
import dev.syntvalley.client.renderer.SyntCitizenRenderer;
import dev.syntvalley.observability.SyntValleyLog;
import dev.syntvalley.registry.ModEntityTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Physical-client bootstrap. Common and dedicated-server code must not reference this class.
 */
@Mod(value = SyntValleyMod.MOD_ID, dist = Dist.CLIENT)
public final class SyntValleyClientMod {
    public SyntValleyClientMod(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(SyntValleyClientMod::registerRenderers);
        SyntValleyLog.logger().info("Initialized SyntValley client bootstrap");
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntityTypes.SYNT_CITIZEN.get(), SyntCitizenRenderer::new);
    }
}
