package dev.syntvalley.domain.identity;

import java.util.Objects;
import java.util.UUID;

/** Stable Village identity, independent of block position and runtime objects. */
public record VillageId(UUID value) {
    public VillageId {
        Objects.requireNonNull(value, "value");
    }

    public static VillageId random() {
        return new VillageId(UUID.randomUUID());
    }

    public static VillageId parse(String value) {
        return new VillageId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
