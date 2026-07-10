package dev.syntvalley.client;

import dev.syntvalley.SyntValleyMod;
import dev.syntvalley.observability.SyntValleyLog;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Physical-client bootstrap. Common and dedicated-server code must not reference this class.
 */
@Mod(value = SyntValleyMod.MOD_ID, dist = Dist.CLIENT)
public final class SyntValleyClientMod {
    public SyntValleyClientMod(ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        SyntValleyLog.logger().info("Initialized SyntValley client bootstrap");
    }
}
