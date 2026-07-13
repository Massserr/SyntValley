package dev.syntvalley.domain.identity;

import java.util.Objects;
import java.util.UUID;

/** Stable Task identity, independent of scheduler ordering and runtime objects. */
public record TaskId(UUID value) {
    public TaskId {
        Objects.requireNonNull(value, "value");
    }

    public static TaskId random() {
        return new TaskId(UUID.randomUUID());
    }

    public static TaskId parse(String value) {
        return new TaskId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
