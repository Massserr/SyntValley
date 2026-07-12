package dev.syntvalley.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.syntvalley.domain.citizen.CitizenAggregate;
import dev.syntvalley.domain.citizen.CitizenEntityBinding;
import dev.syntvalley.domain.citizen.CitizenLifecycle;
import dev.syntvalley.domain.identity.CitizenId;
import dev.syntvalley.domain.identity.VillageId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CitizenBindingServiceTest {
    private static final CitizenId CITIZEN = new CitizenId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final VillageId VILLAGE = new VillageId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
    private static final UUID ENTITY_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ENTITY_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private static CitizenEntityBinding seed(InMemoryCitizenRepository citizens) {
        CitizenAggregate citizen = CitizenAggregate.create(CITIZEN, VILLAGE, "Settler", 0);
        citizens.create(citizen);
        return citizen.entityBinding();
    }

    @Test
    void claimIsIdempotentForTheSameEntity() {
        InMemoryCitizenRepository citizens = new InMemoryCitizenRepository();
        CitizenBindingService service = new CitizenBindingService(citizens);
        CitizenEntityBinding binding = seed(citizens);

        CitizenBindingService.EnsureCitizenResult.Bound claimed = assertInstanceOf(
                CitizenBindingService.EnsureCitizenResult.Bound.class,
                service.ensureBound(binding, ENTITY_A)
        );
        assertEquals(CitizenBindingService.PresenceOutcome.CLAIMED, claimed.outcome());
        assertEquals(2, claimed.citizen().revision());
        assertEquals(ENTITY_A, claimed.citizen().boundEntityId().orElseThrow());

        CitizenBindingService.EnsureCitizenResult.Bound reloaded = assertInstanceOf(
                CitizenBindingService.EnsureCitizenResult.Bound.class,
                service.ensureBound(binding, ENTITY_A)
        );
        assertEquals(CitizenBindingService.PresenceOutcome.EXISTING, reloaded.outcome());
        assertEquals(2, reloaded.citizen().revision());
    }

    @Test
    void duplicateEntityAndMissingRecordAreRejected() {
        InMemoryCitizenRepository citizens = new InMemoryCitizenRepository();
        CitizenBindingService service = new CitizenBindingService(citizens);
        CitizenEntityBinding binding = seed(citizens);
        service.ensureBound(binding, ENTITY_A);

        assertEquals(
                CitizenBindingService.CitizenBindingRejection.DUPLICATE_ENTITY,
                ((CitizenBindingService.EnsureCitizenResult.Rejected) service.ensureBound(binding, ENTITY_B)).reason()
        );

        CitizenBindingService emptyService = new CitizenBindingService(new InMemoryCitizenRepository());
        assertEquals(
                CitizenBindingService.CitizenBindingRejection.MISSING_CITIZEN,
                ((CitizenBindingService.EnsureCitizenResult.Rejected) emptyService.ensureBound(binding, ENTITY_A)).reason()
        );
    }

    @Test
    void deathIsRecordedAndBlocksLaterPresence() {
        InMemoryCitizenRepository citizens = new InMemoryCitizenRepository();
        CitizenBindingService service = new CitizenBindingService(citizens);
        CitizenEntityBinding binding = seed(citizens);
        service.ensureBound(binding, ENTITY_A);

        CitizenBindingService.DeathResult.Recorded died = assertInstanceOf(
                CitizenBindingService.DeathResult.Recorded.class,
                service.recordDeath(binding, ENTITY_A)
        );
        assertTrue(died.changed());
        assertEquals(CitizenLifecycle.DECEASED, died.citizen().lifecycle());

        assertEquals(
                CitizenBindingService.CitizenBindingRejection.LIFECYCLE_DISALLOWS_PRESENCE,
                ((CitizenBindingService.EnsureCitizenResult.Rejected) service.ensureBound(binding, ENTITY_A)).reason()
        );
    }
}
