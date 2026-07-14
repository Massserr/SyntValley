package dev.syntvalley.application.port;

import dev.syntvalley.domain.resource.ResourceKey;
import java.util.Map;

/**
 * A village's explicitly linked, currently loaded resource store (e.g. a Village Storage block). It
 * exposes a bounded snapshot of counts for the ledger cache; the real items stay the source of truth.
 */
public interface ResourceSource {
    Map<ResourceKey, Integer> snapshotCounts();

    /**
     * Physically removes up to {@code amount} units of {@code key} from this store, returning how many
     * were actually removed. The store is the source of truth, so the caller trusts this count — never
     * the ledger cache — and a smaller return means a player emptied the stack after the plan was made.
     */
    int withdraw(ResourceKey key, int amount);
}
