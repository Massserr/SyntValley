package dev.syntvalley.ai.orchestration;

/**
 * A minimal circuit breaker over the LLM backend, driven by an injected clock so tests are exact.
 * Consecutive failures at or above the threshold OPEN the circuit; after the cooldown one HALF_OPEN
 * probe is allowed — its success closes the circuit, its failure re-opens it for another cooldown.
 * Not thread-safe by itself: the executor synchronizes around it.
 */
public final class CircuitBreaker {
    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final int failureThreshold;
    private final long cooldownMillis;

    private State state = State.CLOSED;
    private int consecutiveFailures;
    private long openedAtMillis;
    private boolean probeInFlight;

    public CircuitBreaker(int failureThreshold, long cooldownMillis) {
        if (failureThreshold < 1) {
            throw new IllegalArgumentException("failureThreshold must be positive");
        }
        if (cooldownMillis < 0) {
            throw new IllegalArgumentException("cooldownMillis must not be negative");
        }
        this.failureThreshold = failureThreshold;
        this.cooldownMillis = cooldownMillis;
    }

    /** Whether a request may go out now. In HALF_OPEN exactly one probe is allowed at a time. */
    public boolean allow(long nowMillis) {
        if (state == State.CLOSED) {
            return true;
        }
        if (state == State.OPEN && nowMillis - openedAtMillis >= cooldownMillis) {
            state = State.HALF_OPEN;
            probeInFlight = false;
        }
        if (state == State.HALF_OPEN && !probeInFlight) {
            probeInFlight = true;
            return true;
        }
        return false;
    }

    public void onSuccess() {
        state = State.CLOSED;
        consecutiveFailures = 0;
        probeInFlight = false;
    }

    public void onFailure(long nowMillis) {
        if (state == State.HALF_OPEN) {
            open(nowMillis);
            return;
        }
        consecutiveFailures++;
        if (consecutiveFailures >= failureThreshold) {
            open(nowMillis);
        }
    }

    public State state() {
        return state;
    }

    private void open(long nowMillis) {
        state = State.OPEN;
        openedAtMillis = nowMillis;
        consecutiveFailures = 0;
        probeInFlight = false;
    }
}
