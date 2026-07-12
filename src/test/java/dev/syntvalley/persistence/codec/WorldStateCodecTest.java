package dev.syntvalley.persistence.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.syntvalley.domain.identity.VillageId;
import dev.syntvalley.domain.village.CoreLocation;
import dev.syntvalley.domain.village.VillageAggregate;
import dev.syntvalley.persistence.migration.MigrationRegistry;
import dev.syntvalley.persistence.saveddata.VillagePersistentRecord;
import dev.syntvalley.persistence.saveddata.WorldState;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

class WorldStateCodecTest {
    @Test
    void emptySchemaOneRootRoundTrips() {
        WorldState state = new WorldState(
                UUID.fromString("05b15b2f-a38b-45fa-ab38-cf39ec16cd8f"),
                0,
                1_783_620_000_000L,
                0,
                Map.of()
        );

        assertEquals(state, WorldStateCodec.decode(WorldStateCodec.encode(state)));
    }

    @Test
    void minimalVillageRoundTripsUnicodeAndNegativePosition() {
        VillageId id = new VillageId(UUID.fromString("af23cb27-b660-4d5d-a677-e8f6d74eafbb"));
        long negativePosition = new BlockPos(-321, 47, -987).asLong();
        VillageAggregate village = VillageAggregate.create(
                id,
                "Берёзовая долина",
                new CoreLocation("minecraft:the_nether", negativePosition),
                400
        );
        WorldState state = new WorldState(
                UUID.fromString("97de21a2-19bf-4e0c-b850-126ca8e5d35e"),
                1,
                1_783_620_000_000L,
                350,
                Map.of(id, VillagePersistentRecord.fromNewAggregate(village))
        );

        WorldState decoded = WorldStateCodec.decode(WorldStateCodec.encode(state));
        assertEquals(state, decoded);
        assertEquals(negativePosition, decoded.villages().get(id).coreLocation().orElseThrow().packedPos());
    }

    @Test
    void duplicateVillageIdsAreRejected() {
        VillageId id = new VillageId(UUID.fromString("e6bd3662-97b0-4bd2-a02b-45368df95885"));
        VillageAggregate village = VillageAggregate.create(
                id,
                "Village",
                new CoreLocation("minecraft:overworld", 1),
                0
        );
        WorldState state = new WorldState(
                UUID.randomUUID(),
                1,
                10,
                0,
                Map.of(id, VillagePersistentRecord.fromNewAggregate(village))
        );
        CompoundTag tag = WorldStateCodec.encode(state);
        ListTag villages = tag.getList("villages", Tag.TAG_COMPOUND);
        villages.add(villages.getCompound(0).copy());

        PersistenceException exception = assertThrows(PersistenceException.class, () -> WorldStateCodec.decode(tag));
        assertTrue(exception.diagnostic().contains("duplicate Village ID"));
    }

    @Test
    void unsupportedNonEmptyCollectionIsRejectedInsteadOfDropped() {
        CompoundTag tag = WorldStateCodec.encode(WorldState.createNew(10));
        tag.getList("citizens", Tag.TAG_COMPOUND).add(new CompoundTag());

        PersistenceException exception = assertThrows(PersistenceException.class, () -> WorldStateCodec.decode(tag));
        assertTrue(exception.diagnostic().contains("cannot be discarded"));
    }

    @Test
    void futureSchemaFailsClosedWithoutMutatingSource() {
        CompoundTag source = WorldStateCodec.encode(WorldState.createNew(10));
        source.putInt("schema_version", MigrationRegistry.CURRENT_SCHEMA_VERSION + 1);
        CompoundTag before = source.copy();

        assertThrows(UnsupportedSchemaVersionException.class, () -> MigrationRegistry.current().migrate(source));
        assertEquals(before, source);
    }
}
