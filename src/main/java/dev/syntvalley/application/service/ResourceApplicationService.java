package dev.syntvalley.application.service;

import dev.syntvalley.application.port.ResourceWithdrawal;
import dev.syntvalley.domain.identity.TaskId;
import dev.syntvalley.domain.resource.MealPlanner;
import dev.syntvalley.domain.resource.NutritionTable;
import dev.syntvalley.domain.resource.ResourceKey;
import dev.syntvalley.domain.resource.ResourceLedger;
import dev.syntvalley.domain.resource.ResourceReservations;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Slice 7's one end-to-end delivery task: feed a hungry citizen from the village's explicitly linked
 * storages. Planning runs against the ledger cache minus outstanding reservations; the actual transfer
 * goes through the withdrawal port, which re-checks the physical ItemStacks. The reservation is held
 * only for the duration of the transfer and always released, so a restart — which drops reservations —
 * never double-spends and never leaves a stack logically locked.
 */
public final class ResourceApplicationService {
    /** How long a meal reservation is held before it is treated as abandoned, in game ticks. */
    public static final long RESERVATION_TICKS = 100L;

    private final NutritionTable nutrition;
    private final MealPlanner planner;

    public ResourceApplicationService(NutritionTable nutrition, MealPlanner planner) {
        this.nutrition = Objects.requireNonNull(nutrition, "nutrition");
        this.planner = Objects.requireNonNull(planner, "planner");
    }

    public MealOutcome feed(
            TaskId owner,
            int hunger,
            ResourceLedger ledger,
            ResourceReservations reservations,
            ResourceWithdrawal withdrawal,
            long now) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(ledger, "ledger");
        Objects.requireNonNull(reservations, "reservations");
        Objects.requireNonNull(withdrawal, "withdrawal");

        reservations.expire(now);
        Map<ResourceKey, Integer> available = availableFoods(ledger, reservations);
        Optional<ResourceKey> chosen = planner.choose(hunger, available, nutrition);
        if (chosen.isEmpty()) {
            return MealOutcome.noFood();
        }
        ResourceKey food = chosen.orElseThrow();

        // Reserve against the gross ledger count; reserve() itself subtracts what other owners hold.
        if (!reservations.reserve(owner, food, 1, ledger.count(food), now + RESERVATION_TICKS)) {
            return MealOutcome.reserved();
        }
        try {
            int withdrawn = withdrawal.withdraw(food, 1);
            return withdrawn >= 1 ? MealOutcome.fed(food, nutrition.restores(food)) : MealOutcome.stale();
        } finally {
            reservations.release(owner);
        }
    }

    private Map<ResourceKey, Integer> availableFoods(ResourceLedger ledger, ResourceReservations reservations) {
        Map<ResourceKey, Integer> available = new LinkedHashMap<>();
        ledger.counts().forEach((key, count) -> {
            if (!nutrition.isFood(key)) {
                return;
            }
            int free = count - reservations.reservedFor(key);
            if (free > 0) {
                available.put(key, free);
            }
        });
        return available;
    }
}
