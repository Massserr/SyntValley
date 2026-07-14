package dev.syntvalley.persistence.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.syntvalley.domain.building.BuildingTemplateId;
import dev.syntvalley.domain.building.Rotation;
import dev.syntvalley.domain.building.SitePlacement;
import dev.syntvalley.domain.citizen.CitizenAggregate;
import dev.syntvalley.domain.citizen.CitizenTransitionResult;
import dev.syntvalley.domain.identity.CitizenId;
import dev.syntvalley.domain.identity.VillageId;
import dev.syntvalley.domain.project.BuildProject;
import dev.syntvalley.domain.project.ProjectId;
import dev.syntvalley.domain.project.ProjectPauseReason;
import dev.syntvalley.domain.project.ProjectState;
import dev.syntvalley.domain.project.ProjectTransitionResult;
import dev.syntvalley.domain.village.CoreLocation;
import dev.syntvalley.domain.village.VillageAggregate;
import dev.syntvalley.persistence.migration.MigrationRegistry;
import dev.syntvalley.persistence.saveddata.CitizenPersistentRecord;
import dev.syntvalley.persistence.saveddata.ProjectPersistentRecord;
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
                Map.of(),
                Map.of(),
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
                Map.of(id, VillagePersistentRecord.fromNewAggregate(village)),
                Map.of(),
                Map.of()
        );

        WorldState decoded = WorldStateCodec.decode(WorldStateCodec.encode(state));
        assertEquals(state, decoded);
        assertEquals(negativePosition, decoded.villages().get(id).coreLocation().orElseThrow().packedPos());
    }

    @Test
    void minimalCitizenRoundTripsBoundAndUnbound() {
        VillageId villageId = new VillageId(UUID.fromString("af23cb27-b660-4d5d-a677-e8f6d74eafbb"));
        VillageAggregate village = VillageAggregate.create(
                villageId,
                "Берёзовая долина",
                new CoreLocation("minecraft:overworld", 274_877_911_104L),
                400
        );

        CitizenId boundId = new CitizenId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        UUID entityId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        CitizenAggregate active = CitizenAggregate.create(boundId, villageId, "Мара", 5);
        CitizenAggregate bound = ((CitizenTransitionResult.Changed)
                active.reconcileEntity(active.entityBinding(), entityId)).citizen();

        CitizenId unboundId = new CitizenId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        CitizenAggregate unbound = CitizenAggregate.create(unboundId, villageId, "Settler", 6);

        WorldState state = new WorldState(
                UUID.fromString("97de21a2-19bf-4e0c-b850-126ca8e5d35e"),
                3,
                1_783_620_000_000L,
                350,
                Map.of(villageId, VillagePersistentRecord.fromNewAggregate(village)),
                Map.of(
                        boundId, CitizenPersistentRecord.fromAggregate(bound),
                        unboundId, CitizenPersistentRecord.fromAggregate(unbound)
                ),
                Map.of()
        );

        WorldState decoded = WorldStateCodec.decode(WorldStateCodec.encode(state));
        assertEquals(state, decoded);
        assertEquals(entityId, decoded.citizens().get(boundId).boundEntityId().orElseThrow());
        assertTrue(decoded.citizens().get(unboundId).boundEntityId().isEmpty());
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
                Map.of(id, VillagePersistentRecord.fromNewAggregate(village)),
                Map.of(),
                Map.of()
        );
        CompoundTag tag = WorldStateCodec.encode(state);
        ListTag villages = tag.getList("villages", Tag.TAG_COMPOUND);
        villages.add(villages.getCompound(0).copy());

        PersistenceException exception = assertThrows(PersistenceException.class, () -> WorldStateCodec.decode(tag));
        assertTrue(exception.diagnostic().contains("duplicate Village ID"));
    }

    @Test
    void duplicateCitizenIdsAreRejected() {
        CompoundTag tag = WorldStateCodec.encode(singleCitizenState());
        ListTag citizens = tag.getList("citizens", Tag.TAG_COMPOUND);
        citizens.add(citizens.getCompound(0).copy());

        PersistenceException exception = assertThrows(PersistenceException.class, () -> WorldStateCodec.decode(tag));
        assertTrue(exception.diagnostic().contains("duplicate Citizen ID"));
    }

    @Test
    void citizenReferencingUnknownVillageIsRejected() {
        CompoundTag tag = WorldStateCodec.encode(singleCitizenState());
        tag.getList("citizens", Tag.TAG_COMPOUND).getCompound(0).putUUID("village_id", UUID.randomUUID());

        PersistenceException exception = assertThrows(PersistenceException.class, () -> WorldStateCodec.decode(tag));
        assertTrue(exception.diagnostic().contains("unknown Village"));
    }

    @Test
    void unsupportedNonEmptyReservedCollectionIsRejectedInsteadOfDropped() {
        CompoundTag tag = WorldStateCodec.encode(WorldState.createNew(10));
        tag.getList("tasks", Tag.TAG_COMPOUND).add(new CompoundTag());

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

    @Test
    void buildProjectRoundTripsThroughPause() {
        VillageId villageId = new VillageId(UUID.fromString("af23cb27-b660-4d5d-a677-e8f6d74eafbb"));
        VillageAggregate village = VillageAggregate.create(
                villageId, "Village", new CoreLocation("minecraft:overworld", 1L), 0);
        BuildingTemplateId templateId = new BuildingTemplateId("syntvalley:small_storehouse");
        SitePlacement placement =
                SitePlacement.of("minecraft:overworld", 10, 64, -20, Rotation.CLOCKWISE_90, templateId, 1);

        ProjectId projectId = new ProjectId(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        BuildProject project = BuildProject.create(projectId, villageId, templateId, 1, placement, 26);
        project = ((ProjectTransitionResult.Changed) project.beginBuilding()).project();
        project = ((ProjectTransitionResult.Changed) project.recordBlockPlaced()).project();
        project = ((ProjectTransitionResult.Changed) project.pause(ProjectPauseReason.SITE_OBSTRUCTED)).project();

        WorldState state = new WorldState(
                UUID.fromString("97de21a2-19bf-4e0c-b850-126ca8e5d35e"),
                5,
                1_783_620_000_000L,
                350,
                Map.of(villageId, VillagePersistentRecord.fromNewAggregate(village)),
                Map.of(),
                Map.of(projectId, ProjectPersistentRecord.fromAggregate(project))
        );

        WorldState decoded = WorldStateCodec.decode(WorldStateCodec.encode(state));
        assertEquals(state, decoded);
        BuildProject restored = decoded.projects().get(projectId).toAggregate();
        assertEquals(ProjectState.PAUSED, restored.state());
        assertEquals(ProjectPauseReason.SITE_OBSTRUCTED, restored.pauseReason().orElseThrow());
        assertEquals(1, restored.placedBlocks());
        assertEquals(Rotation.CLOCKWISE_90, restored.placement().rotation());
    }

    private static WorldState singleCitizenState() {
        VillageId villageId = new VillageId(UUID.fromString("af23cb27-b660-4d5d-a677-e8f6d74eafbb"));
        VillageAggregate village = VillageAggregate.create(
                villageId,
                "Village",
                new CoreLocation("minecraft:overworld", 1L),
                0
        );
        CitizenId citizenId = new CitizenId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        CitizenAggregate citizen = CitizenAggregate.create(citizenId, villageId, "Settler", 0);
        return new WorldState(
                UUID.fromString("05b15b2f-a38b-45fa-ab38-cf39ec16cd8f"),
                2,
                10,
                0,
                Map.of(villageId, VillagePersistentRecord.fromNewAggregate(village)),
                Map.of(citizenId, CitizenPersistentRecord.fromAggregate(citizen)),
                Map.of()
        );
    }
}
