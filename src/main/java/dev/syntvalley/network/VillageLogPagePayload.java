package dev.syntvalley.network;

import dev.syntvalley.application.query.VillageLogPage;
import dev.syntvalley.bootstrap.ProjectIdentity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-to-client delivery of one bounded, pre-rendered village log page. */
public record VillageLogPagePayload(VillageLogPage page) implements CustomPacketPayload {
    private static final int MAX_ENCODED_LINES = 1024;

    public static final Type<VillageLogPagePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ProjectIdentity.MOD_ID, "village_log_page"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VillageLogPagePayload> STREAM_CODEC =
            StreamCodec.of(VillageLogPagePayload::encode, VillageLogPagePayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, VillageLogPagePayload payload) {
        VillageLogPage page = payload.page();
        buf.writeBoolean(page.firstPage());
        writeLines(buf, page.memoryLines());
        writeLines(buf, page.decisionLines());
        buf.writeLong(page.nextCursor());
        buf.writeBoolean(page.hasMore());
    }

    private static VillageLogPagePayload decode(RegistryFriendlyByteBuf buf) {
        boolean firstPage = buf.readBoolean();
        List<String> memories = readLines(buf);
        List<String> decisions = readLines(buf);
        long nextCursor = buf.readLong();
        boolean hasMore = buf.readBoolean();
        return new VillageLogPagePayload(new VillageLogPage(firstPage, memories, decisions, nextCursor, hasMore));
    }

    private static void writeLines(RegistryFriendlyByteBuf buf, List<String> lines) {
        buf.writeInt(lines.size());
        for (String line : lines) {
            buf.writeUtf(line);
        }
    }

    private static List<String> readLines(RegistryFriendlyByteBuf buf) {
        int size = buf.readInt();
        if (size < 0 || size > MAX_ENCODED_LINES) {
            throw new IllegalStateException("Encoded line list is out of bounds: " + size);
        }
        List<String> lines = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            lines.add(buf.readUtf());
        }
        return lines;
    }

    @Override
    public Type<VillageLogPagePayload> type() {
        return TYPE;
    }
}
