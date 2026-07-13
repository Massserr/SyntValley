package dev.syntvalley.network;

import dev.syntvalley.application.query.VillageOverviewDto;
import dev.syntvalley.bootstrap.ProjectIdentity;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-to-client Village overview snapshot. */
public record VillageOverviewSnapshotPayload(VillageOverviewDto overview) implements CustomPacketPayload {
    public static final Type<VillageOverviewSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ProjectIdentity.MOD_ID, "village_overview_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VillageOverviewSnapshotPayload> STREAM_CODEC =
            StreamCodec.composite(
                    VillageOverviewStreamCodecs.OVERVIEW,
                    VillageOverviewSnapshotPayload::overview,
                    VillageOverviewSnapshotPayload::new);

    public VillageOverviewSnapshotPayload {
        Objects.requireNonNull(overview, "overview");
    }

    @Override
    public Type<VillageOverviewSnapshotPayload> type() {
        return TYPE;
    }
}
