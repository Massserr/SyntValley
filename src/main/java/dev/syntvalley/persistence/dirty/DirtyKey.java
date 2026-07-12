package dev.syntvalley.persistence.dirty;

import dev.syntvalley.domain.identity.VillageId;
import java.util.Objects;

public record DirtyKey(DirtyKind kind, VillageId villageId) {
    public DirtyKey {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(villageId, "villageId");
    }

    public static DirtyKey village(VillageId villageId) {
        return new DirtyKey(DirtyKind.VILLAGE, villageId);
    }
}
