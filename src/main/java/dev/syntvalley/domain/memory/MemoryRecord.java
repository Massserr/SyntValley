package dev.syntvalley.domain.memory;

import java.util.Objects;

/**
 * One remembered event. The {@code dedupeKey} is the stable identity of the underlying event, so replay
 * or restart re-adding the same event is a no-op. Salience (0..1000) decays over time unless the memory
 * is pinned; {@code subject} names what/who it is about (a citizen or project id, or empty).
 */
public record MemoryRecord(
        String dedupeKey,
        MemoryKind kind,
        MemorySource source,
        String subject,
        int salience,
        long createdGameTime,
        boolean pinned
) {
    public static final int MIN_SALIENCE = 0;
    public static final int MAX_SALIENCE = 1000;

    public MemoryRecord {
        Objects.requireNonNull(dedupeKey, "dedupeKey");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(subject, "subject");
        if (dedupeKey.isBlank()) {
            throw new IllegalArgumentException("dedupeKey must not be blank");
        }
        if (salience < MIN_SALIENCE || salience > MAX_SALIENCE) {
            throw new IllegalArgumentException("salience out of range: " + salience);
        }
        if (createdGameTime < 0) {
            throw new IllegalArgumentException("createdGameTime must not be negative");
        }
    }

    public MemoryRecord withSalience(int newSalience) {
        return new MemoryRecord(dedupeKey, kind, source, subject, clamp(newSalience), createdGameTime, pinned);
    }

    public MemoryRecord withPinned(boolean value) {
        return new MemoryRecord(dedupeKey, kind, source, subject, salience, createdGameTime, value);
    }

    private static int clamp(int value) {
        return Math.max(MIN_SALIENCE, Math.min(MAX_SALIENCE, value));
    }
}
