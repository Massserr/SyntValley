package dev.syntvalley.domain.citizen;

import dev.syntvalley.domain.identity.CitizenId;
import dev.syntvalley.domain.identity.VillageId;
import java.util.Objects;

/**
 * Local binding token stored in the entity NBT. A generation lets stale entity NBT be rejected
 * instead of silently reactivating a Citizen identity.
 */
public record CitizenEntityBinding(CitizenId citizenId, VillageId villageId, int generation) {
    public CitizenEntityBinding {
        Objects.requireNonNull(citizenId, "citizenId");
        Objects.requireNonNull(villageId, "villageId");
        if (generation < 1) {
            throw new IllegalArgumentException("Binding generation must be positive");
        }
    }
}
