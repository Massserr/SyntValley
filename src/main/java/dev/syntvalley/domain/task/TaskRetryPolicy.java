package dev.syntvalley.domain.task;

/** Bounded retry with fixed backoff between attempts. */
public record TaskRetryPolicy(int maxAttempts, long backoffTicks) {
    public TaskRetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        if (backoffTicks < 0) {
            throw new IllegalArgumentException("backoffTicks must not be negative");
        }
    }

    public static TaskRetryPolicy defaults() {
        return new TaskRetryPolicy(3, 40);
    }
}
