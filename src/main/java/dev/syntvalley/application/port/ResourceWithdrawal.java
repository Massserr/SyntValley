package dev.syntvalley.application.port;

import dev.syntvalley.domain.resource.ResourceKey;

/**
 * Physically removes items from a village's linked storages. The adapter re-checks the real ItemStacks
 * at call time — the ledger is only a cache — and returns how many units were actually removed, which
 * may be fewer than requested if a player emptied the container after the plan was made.
 */
public interface ResourceWithdrawal {
    int withdraw(ResourceKey key, int amount);
}
