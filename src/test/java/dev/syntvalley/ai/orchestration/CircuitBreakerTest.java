package dev.syntvalley.ai.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CircuitBreakerTest {
    @Test
    void staysClosedBelowThreshold() {
        CircuitBreaker breaker = new CircuitBreaker(3, 1000);
        breaker.onFailure(0);
        breaker.onFailure(1);
        assertEquals(CircuitBreaker.State.CLOSED, breaker.state());
        assertTrue(breaker.allow(2));
    }

    @Test
    void opensAtThresholdAndBlocksUntilCooldown() {
        CircuitBreaker breaker = new CircuitBreaker(2, 1000);
        breaker.onFailure(0);
        breaker.onFailure(10);
        assertEquals(CircuitBreaker.State.OPEN, breaker.state());
        assertFalse(breaker.allow(500), "still cooling down");
        assertTrue(breaker.allow(1010), "cooldown elapsed allows one probe");
        assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.state());
    }

    @Test
    void halfOpenAllowsExactlyOneProbe() {
        CircuitBreaker breaker = new CircuitBreaker(1, 100);
        breaker.onFailure(0);
        assertTrue(breaker.allow(100));
        assertFalse(breaker.allow(101), "only one probe in flight");
    }

    @Test
    void probeSuccessClosesProbeFailureReopens() {
        CircuitBreaker breaker = new CircuitBreaker(1, 100);
        breaker.onFailure(0);
        assertTrue(breaker.allow(100));
        breaker.onSuccess();
        assertEquals(CircuitBreaker.State.CLOSED, breaker.state());

        breaker.onFailure(200);
        assertTrue(breaker.allow(300));
        breaker.onFailure(301);
        assertEquals(CircuitBreaker.State.OPEN, breaker.state());
        assertFalse(breaker.allow(350), "reopened circuit starts a fresh cooldown");
        assertTrue(breaker.allow(401));
    }

    @Test
    void successResetsConsecutiveFailures() {
        CircuitBreaker breaker = new CircuitBreaker(2, 100);
        breaker.onFailure(0);
        breaker.onSuccess();
        breaker.onFailure(1);
        assertEquals(CircuitBreaker.State.CLOSED, breaker.state(), "failures are consecutive, not cumulative");
    }
}
