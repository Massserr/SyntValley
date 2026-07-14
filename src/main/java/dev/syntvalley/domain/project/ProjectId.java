package dev.syntvalley.domain.project;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of a build project, independent of scheduler ordering and runtime objects. */
public record ProjectId(UUID value) {
    public ProjectId {
        Objects.requireNonNull(value, "value");
    }

    public static ProjectId random() {
        return new ProjectId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
