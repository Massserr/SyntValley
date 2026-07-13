package dev.syntvalley.client.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.syntvalley.application.query.VillageOverviewDto;
import java.util.List;
import org.junit.jupiter.api.Test;

class VillageOverviewCacheTest {
    private static VillageOverviewDto snapshot(long revision) {
        return new VillageOverviewDto("v", "Village", "ACTIVE", revision, true, 0, false, List.of());
    }

    @Test
    void opensLoadingThenAcceptsSnapshot() {
        VillageOverviewCache cache = new VillageOverviewCache();
        cache.requestOpen();
        assertEquals(VillageOverviewCache.State.LOADING, cache.state());

        assertTrue(cache.accept(snapshot(1)));
        assertEquals(VillageOverviewCache.State.LOADED, cache.state());
        assertEquals(1, cache.revision());
        assertTrue(cache.current().isPresent());
    }

    @Test
    void ignoresOlderOrEqualRevisions() {
        VillageOverviewCache cache = new VillageOverviewCache();
        cache.requestOpen();
        assertTrue(cache.accept(snapshot(5)));

        assertFalse(cache.accept(snapshot(3)), "older revision must be ignored");
        assertFalse(cache.accept(snapshot(5)), "equal revision must be ignored");
        assertEquals(5, cache.revision());

        assertTrue(cache.accept(snapshot(7)), "newer revision must apply");
        assertEquals(7, cache.revision());
    }

    @Test
    void staleErrorAndCloseTransitions() {
        VillageOverviewCache cache = new VillageOverviewCache();
        cache.requestOpen();
        cache.accept(snapshot(1));

        cache.markStale();
        assertEquals(VillageOverviewCache.State.STALE, cache.state());

        assertTrue(cache.accept(snapshot(2)), "a fresh snapshot recovers from stale");
        assertEquals(VillageOverviewCache.State.LOADED, cache.state());

        cache.fail();
        assertEquals(VillageOverviewCache.State.ERROR, cache.state());

        cache.close();
        assertEquals(VillageOverviewCache.State.CLOSED, cache.state());
        assertTrue(cache.current().isEmpty());
        assertFalse(cache.accept(snapshot(9)), "closed cache rejects snapshots");
    }
}
