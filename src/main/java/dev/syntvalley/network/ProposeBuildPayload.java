package dev.syntvalley.network;

import dev.syntvalley.bootstrap.ProjectIdentity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-server request to propose a build for the village the player currently has open. Carries no
 * data: the server uses the viewer's open overview session to know which village, so a client can never
 * name someone else's village or free-form coordinates.
 */
public record ProposeBuildPayload() implements CustomPacketPayload {
    public static final ProposeBuildPayload INSTANCE = new ProposeBuildPayload();

    public static final Type<ProposeBuildPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ProjectIdentity.MOD_ID, "propose_build"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProposeBuildPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<ProposeBuildPayload> type() {
        return TYPE;
    }
}
