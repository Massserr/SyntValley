package dev.syntvalley.domain.village;

import dev.syntvalley.domain.identity.VillageId;
import java.util.Objects;

/** Local Core binding token. A generation prevents stale block NBT from winning a rebind. */
public record CoreBinding(VillageId villageId, int generation) {
    public CoreBinding {
        Objects.requireNonNull(villageId, "villageId");
        if (generation < 1) {
            throw new IllegalArgumentException("Binding generation must be positive");
        }
    }
}
