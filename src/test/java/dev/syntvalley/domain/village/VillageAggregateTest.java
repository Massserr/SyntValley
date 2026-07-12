package dev.syntvalley.domain.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.syntvalley.domain.identity.VillageId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VillageAggregateTest {
    private static final VillageId ID = new VillageId(UUID.fromString("0a9e62f0-27f8-47ab-9378-d8d815e6f81c"));
    private static final CoreLocation LOCATION = new CoreLocation("minecraft:overworld", -123456789L);

    @Test
    void coreRemovalAndRebindPreserveIdentityAndAdvanceRevisions() {
        VillageAggregate created = VillageAggregate.create(ID, "Берёзовая долина", LOCATION, 20);
        assertEquals(VillageLifecycle.ACTIVE, created.lifecycle());
        assertEquals(1, created.revision());
        assertEquals(1, created.lastBindingGeneration());

        VillageTransitionResult.Changed orphanTransition = assertInstanceOf(
                VillageTransitionResult.Changed.class,
                created.orphanCore(created.coreBinding(), LOCATION)
        );
        VillageAggregate orphaned = orphanTransition.village();
        assertEquals(VillageLifecycle.ORPHANED, orphaned.lifecycle());
        assertTrue(orphaned.coreLocation().isEmpty());
        assertEquals(2, orphaned.revision());
        assertEquals(1, orphaned.lastBindingGeneration());

        assertInstanceOf(
                VillageTransitionResult.Unchanged.class,
                orphaned.orphanCore(created.coreBinding(), LOCATION)
        );

        VillageTransitionResult.Changed reboundTransition = assertInstanceOf(
                VillageTransitionResult.Changed.class,
                orphaned.reconcileCore(created.coreBinding(), LOCATION)
        );
        VillageAggregate rebound = reboundTransition.village();
        assertEquals(ID, rebound.id());
        assertEquals(VillageLifecycle.ACTIVE, rebound.lifecycle());
        assertEquals(3, rebound.revision());
        assertEquals(2, rebound.lastBindingGeneration());
    }

    @Test
    void staleOrConflictingBindingsFailClosed() {
        VillageAggregate created = VillageAggregate.create(ID, "Village", LOCATION, 0);
        assertInstanceOf(
                VillageTransitionResult.Rejected.class,
                created.reconcileCore(created.coreBinding(), new CoreLocation("minecraft:the_nether", 42))
        );

        VillageAggregate orphaned = ((VillageTransitionResult.Changed)
                created.orphanCore(created.coreBinding(), LOCATION)).village();
        VillageTransitionResult.Changed rebound = (VillageTransitionResult.Changed)
                orphaned.reconcileCore(created.coreBinding(), LOCATION);

        VillageTransitionResult.Rejected stale = assertInstanceOf(
                VillageTransitionResult.Rejected.class,
                rebound.village().reconcileCore(created.coreBinding(), LOCATION)
        );
        assertEquals(VillageTransitionRejection.BINDING_CONFLICT, stale.reason());
    }
}
