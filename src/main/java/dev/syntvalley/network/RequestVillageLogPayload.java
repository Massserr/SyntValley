package dev.syntvalley.network;

import dev.syntvalley.bootstrap.ProjectIdentity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-server request for one page of the read-only village log. Carries only the pagination
 * cursor: the server resolves the village from the viewer's open overview session, so a client can never
 * name someone else's village. {@code Long.MAX_VALUE} asks for the first (newest) page.
 */
public record RequestVillageLogPayload(long beforeSequence) implements CustomPacketPayload {
    public static final Type<RequestVillageLogPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ProjectIdentity.MOD_ID, "request_village_log"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestVillageLogPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeLong(payload.beforeSequence()),
                    buf -> new RequestVillageLogPayload(buf.readLong()));

    public static RequestVillageLogPayload firstPage() {
        return new RequestVillageLogPayload(Long.MAX_VALUE);
    }

    @Override
    public Type<RequestVillageLogPayload> type() {
        return TYPE;
    }
}
