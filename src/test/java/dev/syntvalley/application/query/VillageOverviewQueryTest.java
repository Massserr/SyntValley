package dev.syntvalley.application.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.syntvalley.application.port.RepositoryCommitResult;
import dev.syntvalley.application.port.VillageStateRepository;
import dev.syntvalley.application.service.InMemoryCitizenRepository;
import dev.syntvalley.domain.citizen.CitizenAggregate;
import dev.syntvalley.domain.citizen.CitizenTransitionResult;
import dev.syntvalley.domain.identity.CitizenId;
import dev.syntvalley.domain.identity.VillageId;
import dev.syntvalley.domain.village.CoreLocation;
import dev.syntvalley.domain.village.VillageAggregate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VillageOverviewQueryTest {
    private static final VillageId VILLAGE = new VillageId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
    private static final CoreLocation LOCATION = new CoreLocation("minecraft:overworld", 42L);

    @Test
    void overviewMapsVillageAndResidentPresence() {
        InMemoryCitizenRepository citizens = new InMemoryCitizenRepository();

        CitizenAggregate active = CitizenAggregate.create(
                new CitizenId(UUID.fromString("11111111-1111-1111-1111-111111111111")), VILLAGE, "Мара", 5);
        citizens.create(active);
        CitizenAggregate bound = ((CitizenTransitionResult.Changed)
                active.reconcileEntity(active.entityBinding(), UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")))
                .citizen();
        citizens.update(bound, active.revision());

        citizens.create(CitizenAggregate.create(
                new CitizenId(UUID.fromString("33333333-3333-3333-3333-333333333333")), VILLAGE, "Settler", 6));

        VillageOverviewQuery query = new VillageOverviewQuery(
                new StubVillages(VillageAggregate.create(VILLAGE, "Village", LOCATION, 0)), citizens);
        VillageOverviewDto dto = query.overview(VILLAGE).orElseThrow();

        assertEquals(VILLAGE.toString(), dto.villageId());
        assertEquals("ACTIVE", dto.lifecycle());
        assertTrue(dto.coreBound());
        assertEquals(2, dto.residentCount());
        assertFalse(dto.residentsTruncated());
        assertEquals(2, dto.residents().size());
        assertTrue(dto.residents().stream().anyMatch(VillageOverviewDto.CitizenOverviewEntry::present));
        assertTrue(dto.residents().stream().anyMatch(resident -> !resident.present()));
    }

    @Test
    void overviewReportsTruncationBeyondCap() {
        InMemoryCitizenRepository citizens = new InMemoryCitizenRepository();
        for (int index = 0; index < VillageOverviewQuery.MAX_OVERVIEW_RESIDENTS + 5; index++) {
            citizens.create(CitizenAggregate.create(CitizenId.random(), VILLAGE, "Settler", index));
        }

        VillageOverviewQuery query = new VillageOverviewQuery(
                new StubVillages(VillageAggregate.create(VILLAGE, "Village", LOCATION, 0)), citizens);
        VillageOverviewDto dto = query.overview(VILLAGE).orElseThrow();

        assertEquals(VillageOverviewQuery.MAX_OVERVIEW_RESIDENTS + 5, dto.residentCount());
        assertEquals(VillageOverviewQuery.MAX_OVERVIEW_RESIDENTS, dto.residents().size());
        assertTrue(dto.residentsTruncated());
    }

    @Test
    void overviewMissingVillageIsEmpty() {
        VillageOverviewQuery query = new VillageOverviewQuery(new StubVillages(null), new InMemoryCitizenRepository());
        assertTrue(query.overview(VILLAGE).isEmpty());
    }

    private static final class StubVillages implements VillageStateRepository {
        private final Optional<VillageAggregate> village;

        StubVillages(VillageAggregate village) {
            this.village = Optional.ofNullable(village);
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public Optional<VillageAggregate> find(VillageId villageId) {
            return village.filter(candidate -> candidate.id().equals(villageId));
        }

        @Override
        public int villageCount() {
            return village.isPresent() ? 1 : 0;
        }

        @Override
        public RepositoryCommitResult create(VillageAggregate candidate) {
            throw new UnsupportedOperationException("read-only stub");
        }

        @Override
        public RepositoryCommitResult update(VillageAggregate candidate, long expectedRevision) {
            throw new UnsupportedOperationException("read-only stub");
        }
    }
}
