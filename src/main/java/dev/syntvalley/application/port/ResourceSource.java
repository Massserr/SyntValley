package dev.syntvalley.application.port;

import dev.syntvalley.domain.resource.ResourceKey;
import java.util.Map;

/**
 * A village's explicitly linked, currently loaded resource store (e.g. a Village Storage block). It
 * exposes a bounded snapshot of counts for the ledger cache; the real items stay the source of truth.
 */
public interface ResourceSource {
    Map<ResourceKey, Integer> snapshotCounts();
}
