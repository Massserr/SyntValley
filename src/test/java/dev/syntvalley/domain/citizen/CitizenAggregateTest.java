package dev.syntvalley.domain.citizen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.syntvalley.domain.identity.CitizenId;
import dev.syntvalley.domain.identity.VillageId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CitizenAggregateTest {
    private static final CitizenId CITIZEN = new CitizenId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final VillageId VILLAGE = new VillageId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
    private static final UUID ENTITY_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ENTITY_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Test
    void bindingPresenceClaimsEntityThenRejectsDuplicate() {
        CitizenAggregate created = CitizenAggregate.create(CITIZEN, VILLAGE, "Мара", 5);
        assertEquals(CitizenLifecycle.ACTIVE, created.lifecycle());
        assertEquals(1, created.revision());
        assertEquals(1, created.bindingGeneration());
        assertTrue(created.boundEntityId().isEmpty());

        CitizenTransitionResult.Changed claim = assertInstanceOf(
                CitizenTransitionResult.Changed.class,
                created.reconcileEntity(created.entityBinding(), ENTITY_A)
        );
        CitizenAggregate bound = claim.citizen();
        assertEquals(2, bound.revision());
        assertEquals(ENTITY_A, bound.boundEntityId().orElseThrow());

        assertInstanceOf(
                CitizenTransitionResult.Unchanged.class,
                bound.reconcileEntity(bound.entityBinding(), ENTITY_A)
        );

        CitizenTransitionResult.Rejected duplicate = assertInstanceOf(
                CitizenTransitionResult.Rejected.class,
                bound.reconcileEntity(bound.entityBinding(), ENTITY_B)
        );
        assertEquals(CitizenTransitionRejection.DUPLICATE_ENTITY, duplicate.reason());
    }

    @Test
    void staleGenerationAndForeignBindingFailClosed() {
        CitizenAggregate created = CitizenAggregate.create(CITIZEN, VILLAGE, "Settler", 0);

        CitizenTransitionResult.Rejected stale = assertInstanceOf(
                CitizenTransitionResult.Rejected.class,
                created.reconcileEntity(new CitizenEntityBinding(CITIZEN, VILLAGE, 2), ENTITY_A)
        );
        assertEquals(CitizenTransitionRejection.STALE_BINDING, stale.reason());

        VillageId otherVillage = new VillageId(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        CitizenTransitionResult.Rejected foreign = assertInstanceOf(
                CitizenTransitionResult.Rejected.class,
                created.reconcileEntity(new CitizenEntityBinding(CITIZEN, otherVillage, 1), ENTITY_A)
        );
        assertEquals(CitizenTransitionRejection.BINDING_CONFLICT, foreign.reason());
    }

    @Test
    void deathIsTerminalAndRejectsImpostorEntity() {
        CitizenAggregate bound = ((CitizenTransitionResult.Changed)
                CitizenAggregate.create(CITIZEN, VILLAGE, "Settler", 0)
                        .reconcileEntity(new CitizenEntityBinding(CITIZEN, VILLAGE, 1), ENTITY_A)).citizen();

        CitizenTransitionResult.Rejected impostor = assertInstanceOf(
                CitizenTransitionResult.Rejected.class,
                bound.recordDeath(ENTITY_B)
        );
        assertEquals(CitizenTransitionRejection.BINDING_CONFLICT, impostor.reason());

        CitizenTransitionResult.Changed died = assertInstanceOf(
                CitizenTransitionResult.Changed.class,
                bound.recordDeath(ENTITY_A)
        );
        CitizenAggregate deceased = died.citizen();
        assertEquals(CitizenLifecycle.DECEASED, deceased.lifecycle());
        assertTrue(deceased.boundEntityId().isEmpty());
        assertEquals(3, deceased.revision());

        CitizenTransitionResult.Rejected afterDeath = assertInstanceOf(
                CitizenTransitionResult.Rejected.class,
                deceased.reconcileEntity(new CitizenEntityBinding(CITIZEN, VILLAGE, 1), ENTITY_A)
        );
        assertEquals(CitizenTransitionRejection.LIFECYCLE_DISALLOWS_PRESENCE, afterDeath.reason());

        assertInstanceOf(
                CitizenTransitionResult.Unchanged.class,
                deceased.recordDeath(ENTITY_A)
        );
    }
}
