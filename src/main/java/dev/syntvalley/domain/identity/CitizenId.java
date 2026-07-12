package dev.syntvalley.domain.identity;

import java.util.Objects;
import java.util.UUID;

/** Stable Citizen identity, independent of the Minecraft entity UUID and runtime entity id. */
public record CitizenId(UUID value) {
    public CitizenId {
        Objects.requireNonNull(value, "value");
    }

    public static CitizenId random() {
        return new CitizenId(UUID.randomUUID());
    }

    public static CitizenId parse(String value) {
        return new CitizenId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
