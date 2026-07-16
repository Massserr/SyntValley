package dev.syntvalley.bootstrap;

import dev.syntvalley.observability.SyntValleyLog;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Registers server runtime lifecycle and persistence bookkeeping hooks on the game event bus. */
public final class ServerLifecycleSubscriber {
    private ServerLifecycleSubscriber() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ServerLifecycleSubscriber::onServerStarted);
        NeoForge.EVENT_BUS.addListener(ServerLifecycleSubscriber::onServerTick);
        NeoForge.EVENT_BUS.addListener(ServerLifecycleSubscriber::onLevelSaved);
        NeoForge.EVENT_BUS.addListener(ServerLifecycleSubscriber::onServerStopping);
        NeoForge.EVENT_BUS.addListener(ServerLifecycleSubscriber::onServerStopped);
        NeoForge.EVENT_BUS.addListener(ServerLifecycleSubscriber::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(SyntValleyCommands::onRegisterCommands);
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerRuntimeManager.find(player.getServer())
                    .ifPresent(runtime -> runtime.closeOverview(player.getUUID()));
        }
    }

    private static void onServerStarted(ServerStartedEvent event) {
        SyntValleyServerRuntime runtime = ServerRuntimeManager.getOrCreate(event.getServer());
        if (runtime.isPersistenceAvailable()) {
            SyntValleyLog.logger().info("SyntValley world runtime ready with {} Village records", runtime.villageCount());
        } else {
            SyntValleyLog.logger().error(
                    "SyntValley world runtime is persistence-disabled: {}",
                    runtime.persistenceFailureDiagnostic().orElse("unknown persistence failure")
            );
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        ServerRuntimeManager.find(event.getServer())
                .ifPresent(runtime -> runtime.onServerTick(event.getServer().overworld().getGameTime()));
    }

    private static void onLevelSaved(LevelEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel level && level == level.getServer().overworld()) {
            ServerRuntimeManager.find(level.getServer())
                    .ifPresent(runtime -> runtime.onPostWorldSave(level.getGameTime()));
        }
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        ServerRuntimeManager.find(event.getServer())
                .ifPresent(runtime -> runtime.stop(event.getServer().overworld().getGameTime()));
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        ServerRuntimeManager.remove(event.getServer());
    }
}
