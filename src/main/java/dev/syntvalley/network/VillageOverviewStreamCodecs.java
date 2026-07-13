package dev.syntvalley.network;

import dev.syntvalley.application.query.VillageOverviewDto;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Manual StreamCodec for the overview DTO, using only primitive buffer ops to stay simple and safe. */
public final class VillageOverviewStreamCodecs {
    private static final int MAX_ENCODED_RESIDENTS = 8192;

    public static final StreamCodec<RegistryFriendlyByteBuf, VillageOverviewDto> OVERVIEW = StreamCodec.of(
            VillageOverviewStreamCodecs::encode,
            VillageOverviewStreamCodecs::decode
    );

    private VillageOverviewStreamCodecs() {
    }

    private static void encode(RegistryFriendlyByteBuf buf, VillageOverviewDto dto) {
        buf.writeUtf(dto.villageId());
        buf.writeUtf(dto.name());
        buf.writeUtf(dto.lifecycle());
        buf.writeLong(dto.revision());
        buf.writeBoolean(dto.coreBound());
        buf.writeInt(dto.residentCount());
        buf.writeBoolean(dto.residentsTruncated());
        buf.writeInt(dto.residents().size());
        for (VillageOverviewDto.CitizenOverviewEntry entry : dto.residents()) {
            buf.writeUtf(entry.citizenId());
            buf.writeUtf(entry.name());
            buf.writeUtf(entry.lifecycle());
            buf.writeBoolean(entry.present());
        }
    }

    private static VillageOverviewDto decode(RegistryFriendlyByteBuf buf) {
        String villageId = buf.readUtf();
        String name = buf.readUtf();
        String lifecycle = buf.readUtf();
        long revision = buf.readLong();
        boolean coreBound = buf.readBoolean();
        int residentCount = buf.readInt();
        boolean residentsTruncated = buf.readBoolean();
        int size = buf.readInt();
        if (size < 0 || size > MAX_ENCODED_RESIDENTS) {
            throw new IllegalStateException("Encoded resident list is out of bounds: " + size);
        }
        List<VillageOverviewDto.CitizenOverviewEntry> residents = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            residents.add(new VillageOverviewDto.CitizenOverviewEntry(
                    buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readBoolean()));
        }
        return new VillageOverviewDto(villageId, name, lifecycle, revision, coreBound, residentCount, residentsTruncated, residents);
    }
}
