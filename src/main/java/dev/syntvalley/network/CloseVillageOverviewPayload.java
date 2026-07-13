package dev.syntvalley.network;

import dev.syntvalley.bootstrap.ProjectIdentity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client-to-server signal that the overview screen closed, so the server can drop the session. */
public record CloseVillageOverviewPayload() implements CustomPacketPayload {
    public static final CloseVillageOverviewPayload INSTANCE = new CloseVillageOverviewPayload();

    public static final Type<CloseVillageOverviewPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ProjectIdentity.MOD_ID, "close_village_overview"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CloseVillageOverviewPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<CloseVillageOverviewPayload> type() {
        return TYPE;
    }
}
