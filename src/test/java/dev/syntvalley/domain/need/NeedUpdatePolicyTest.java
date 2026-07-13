package dev.syntvalley.domain.need;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class NeedUpdatePolicyTest {
    private static final NeedDecayRates RATES = new NeedDecayRates(100, 5, 3);

    @Test
    void consumesWholeIntervalsAndCarriesRemainder() {
        Needs decayed = NeedUpdatePolicy.advance(Needs.full(0), 250, RATES);

        assertEquals(990, decayed.hunger());
        assertEquals(994, decayed.rest());
        assertEquals(200, decayed.lastUpdatedGameTime(), "50-tick remainder is carried forward");

        Needs next = NeedUpdatePolicy.advance(decayed, 300, RATES);
        assertEquals(985, next.hunger());
        assertEquals(991, next.rest());
        assertEquals(300, next.lastUpdatedGameTime());
    }

    @Test
    void doesNotAdvanceWithinASingleInterval() {
        Needs needs = Needs.full(0);
        assertSame(needs, NeedUpdatePolicy.advance(needs, 50, RATES), "sub-interval elapsed is a no-op");
    }

    @Test
    void clampsAtZero() {
        Needs low = new Needs(20, 10, 0);
        Needs decayed = NeedUpdatePolicy.advance(low, 100_000, RATES);
        assertEquals(0, decayed.hunger());
        assertEquals(0, decayed.rest());
    }
}
