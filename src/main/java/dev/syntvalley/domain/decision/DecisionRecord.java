package dev.syntvalley.domain.decision;

import java.util.Objects;

/**
 * One audited decision: what was chosen, for whom, by which source, and a short human-readable reason.
 * The {@code sequence} is a monotonic id assigned by the log, used as a stable pagination cursor.
 */
public record DecisionRecord(
        long sequence,
        DecisionKind kind,
        String subject,
        String chosen,
        DecisionSource source,
        String reason,
        long gameTime
) {
    public DecisionRecord {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(chosen, "chosen");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(reason, "reason");
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        if (gameTime < 0) {
            throw new IllegalArgumentException("gameTime must not be negative");
        }
    }
}
