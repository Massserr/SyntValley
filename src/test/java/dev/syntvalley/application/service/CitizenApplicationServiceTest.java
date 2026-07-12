package dev.syntvalley.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.syntvalley.application.port.RepositoryCommitResult;
import dev.syntvalley.application.port.VillageStateRepository;
import dev.syntvalley.domain.citizen.CitizenConstraints;
import dev.syntvalley.domain.identity.CitizenId;
import dev.syntvalley.domain.identity.VillageId;
import dev.syntvalley.domain.village.CoreLocation;
import dev.syntvalley.domain.village.VillageAggregate;
import dev.syntvalley.domain.village.VillageTransitionResult;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CitizenApplicationServiceTest {
    private static final VillageId VILLAGE = new VillageId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
    private static final CoreLocation LOCATION = new CoreLocation("minecraft:overworld", 42L);

    @Test
    void hireIntoActiveVillageCreatesBoundCitizen() {
        InMemoryCitizenRepository citizens = new InMemoryCitizenRepository();
        CitizenApplicationService service =
                new CitizenApplicationService(citizens, StubVillages.withActive(), CitizenId::random);

        CitizenApplicationService.HireResult.Hired hired = assertInstanceOf(
                CitizenApplicationService.HireResult.Hired.class,
                service.hire(VILLAGE, "Мара", 7)
        );
        assertEquals(VILLAGE, hired.citizen().villageId());
        assertEquals("Мара", hired.citizen().name());
        assertEquals(1, citizens.citizenCount());
    }

    @Test
    void hireRejectsMissingAndInactiveVillage() {
        InMemoryCitizenRepository citizens = new InMemoryCitizenRepository();

        CitizenApplicationService missing =
                new CitizenApplicationService(citizens, StubVillages.empty(), CitizenId::random);
        assertEquals(
                CitizenApplicationService.HireRejection.MISSING_VILLAGE,
                ((CitizenApplicationService.HireResult.Rejected) missing.hire(VILLAGE, "A", 0)).reason()
        );

        CitizenApplicationService inactive =
                new CitizenApplicationService(citizens, StubVillages.withOrphaned(), CitizenId::random);
        assertEquals(
                CitizenApplicationService.HireRejection.VILLAGE_NOT_ACTIVE,
                ((CitizenApplicationService.HireResult.Rejected) inactive.hire(VILLAGE, "A", 0)).reason()
        );
    }

    @Test
    void hireRejectsWhenVillageAtCitizenCap() {
        InMemoryCitizenRepository citizens = new InMemoryCitizenRepository();
        CitizenApplicationService service =
                new CitizenApplicationService(citizens, StubVillages.withActive(), CitizenId::random);

        for (int index = 0; index < CitizenConstraints.MAX_CITIZENS_PER_VILLAGE; index++) {
            assertInstanceOf(
                    CitizenApplicationService.HireResult.Hired.class,
                    service.hire(VILLAGE, "Settler", index)
            );
        }
        assertEquals(
                CitizenApplicationService.HireRejection.VILLAGE_FULL,
                ((CitizenApplicationService.HireResult.Rejected) service.hire(VILLAGE, "Settler", 99)).reason()
        );
        assertEquals(CitizenConstraints.MAX_CITIZENS_PER_VILLAGE, citizens.citizenCount());
    }

    /** Minimal read-only VillageStateRepository stub; hire only reads availability and the record. */
    private static final class StubVillages implements VillageStateRepository {
        private final Optional<VillageAggregate> village;

        private StubVillages(Optional<VillageAggregate> village) {
            this.village = village;
        }

        static StubVillages empty() {
            return new StubVillages(Optional.empty());
        }

        static StubVillages withActive() {
            return new StubVillages(Optional.of(VillageAggregate.create(VILLAGE, "Village", LOCATION, 0)));
        }

        static StubVillages withOrphaned() {
            VillageAggregate active = VillageAggregate.create(VILLAGE, "Village", LOCATION, 0);
            VillageAggregate orphaned = ((VillageTransitionResult.Changed)
                    active.orphanCore(active.coreBinding(), LOCATION)).village();
            return new StubVillages(Optional.of(orphaned));
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
        public RepositoryCommitResult create(VillageAggregate village) {
            throw new UnsupportedOperationException("hire does not create villages");
        }

        @Override
        public RepositoryCommitResult update(VillageAggregate village, long expectedRevision) {
            throw new UnsupportedOperationException("hire does not update villages");
        }
    }
}
