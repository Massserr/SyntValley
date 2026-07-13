package dev.syntvalley.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.syntvalley.application.port.ResourceWithdrawal;
import dev.syntvalley.domain.identity.TaskId;
import dev.syntvalley.domain.resource.MealPlanner;
import dev.syntvalley.domain.resource.NutritionTable;
import dev.syntvalley.domain.resource.ResourceKey;
import dev.syntvalley.domain.resource.ResourceLedger;
import dev.syntvalley.domain.resource.ResourceReservations;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResourceApplicationServiceTest {
    private static final ResourceKey BREAD = new ResourceKey("minecraft:bread");
    private static final NutritionTable NUTRITION = new NutritionTable(Map.of(BREAD, 400));

    private final ResourceApplicationService service =
            new ResourceApplicationService(NUTRITION, new MealPlanner(600));

    private static ResourceLedger ledgerOf(int bread) {
        Map<ResourceKey, Integer> counts = new LinkedHashMap<>();
        if (bread > 0) {
            counts.put(BREAD, bread);
        }
        return new ResourceLedger(counts, 0L);
    }

    /** Withdrawal stub: hands out up to {@code physical} units total and records what was demanded. */
    private static final class StubWithdrawal implements ResourceWithdrawal {
        private int physical;
        private int lastRequested = -1;

        StubWithdrawal(int physical) {
            this.physical = physical;
        }

        @Override
        public int withdraw(ResourceKey key, int amount) {
            lastRequested = amount;
            int given = Math.min(amount, physical);
            physical -= given;
            return given;
        }
    }

    @Test
    void feedsWhenLedgerAndInventoryAgree() {
        ResourceReservations reservations = new ResourceReservations();
        MealOutcome outcome = service.feed(TaskId.random(), 100, ledgerOf(5), reservations, new StubWithdrawal(5), 10L);
        assertEquals(MealOutcome.Result.FED, outcome.result());
        assertEquals(400, outcome.hungerRestored());
        assertEquals(0, reservations.size(), "reservation is always released after a transfer");
    }

    @Test
    void reportsNoFoodWhenLedgerEmpty() {
        MealOutcome outcome =
                service.feed(TaskId.random(), 100, ledgerOf(0), new ResourceReservations(), new StubWithdrawal(0), 10L);
        assertEquals(MealOutcome.Result.NO_FOOD, outcome.result());
    }

    @Test
    void reportsStaleWhenInventoryEmptiedAfterPlan() {
        ResourceReservations reservations = new ResourceReservations();
        // Ledger cache still claims 5 loaves, but the physical container has been emptied by a player.
        MealOutcome outcome = service.feed(TaskId.random(), 100, ledgerOf(5), reservations, new StubWithdrawal(0), 10L);
        assertEquals(MealOutcome.Result.STALE, outcome.result());
        assertEquals(0, reservations.size());
    }

    @Test
    void secondTaskCannotSpendAReservedStack() {
        ResourceReservations reservations = new ResourceReservations();
        assertTrue(reservations.reserve(TaskId.random(), BREAD, 1, 1, 1000L), "pre-reserve the only loaf");
        StubWithdrawal withdrawal = new StubWithdrawal(1);
        MealOutcome outcome = service.feed(TaskId.random(), 100, ledgerOf(1), reservations, withdrawal, 10L);
        assertEquals(MealOutcome.Result.NO_FOOD, outcome.result(), "the only stack is already reserved");
        assertEquals(-1, withdrawal.lastRequested, "no physical withdrawal is attempted");
    }
}
