package dev.syntvalley.network;

import dev.syntvalley.bootstrap.ServerRuntimeManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers SyntValley network payloads and their directional handlers on the mod event bus. */
public final class SyntValleyNetwork {
    private static final String VERSION = "1";

    private SyntValleyNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToClient(
                VillageOverviewSnapshotPayload.TYPE,
                VillageOverviewSnapshotPayload.STREAM_CODEC,
                SyntValleyNetwork::handleSnapshot);
        registrar.playToServer(
                CloseVillageOverviewPayload.TYPE,
                CloseVillageOverviewPayload.STREAM_CODEC,
                SyntValleyNetwork::handleClose);
        registrar.playToServer(
                ProposeBuildPayload.TYPE,
                ProposeBuildPayload.STREAM_CODEC,
                SyntValleyNetwork::handleProposeBuild);
        registrar.playToServer(
                RequestVillageLogPayload.TYPE,
                RequestVillageLogPayload.STREAM_CODEC,
                SyntValleyNetwork::handleRequestVillageLog);
        registrar.playToClient(
                VillageLogPagePayload.TYPE,
                VillageLogPagePayload.STREAM_CODEC,
                SyntValleyNetwork::handleVillageLogPage);
    }

    private static void handleRequestVillageLog(RequestVillageLogPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ServerRuntimeManager.find(player.getServer()).ifPresent(runtime ->
                        runtime.villageLogPage(player.getUUID(), payload.beforeSequence())
                                .ifPresent(page -> PacketDistributor.sendToPlayer(
                                        player, new VillageLogPagePayload(page))));
            }
        });
    }

    private static void handleVillageLogPage(VillageLogPagePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientLogDispatch.accept(payload.page()));
    }

    private static void handleProposeBuild(ProposeBuildPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ServerRuntimeManager.find(player.getServer()).ifPresent(runtime ->
                        runtime.proposeBuildForViewer(player.getUUID(), player.serverLevel().getGameTime())
                                .ifPresent(overview -> PacketDistributor.sendToPlayer(
                                        player, new VillageOverviewSnapshotPayload(overview))));
            }
        });
    }

    private static void handleSnapshot(VillageOverviewSnapshotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientOverviewDispatch.accept(payload.overview()));
    }

    private static void handleClose(CloseVillageOverviewPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ServerRuntimeManager.find(player.getServer())
                        .ifPresent(runtime -> runtime.closeOverview(player.getUUID()));
            }
        });
    }
}
