package dev.syntvalley.persistence.dirty;

import java.util.Objects;
import java.util.Set;

public record DirtyEntry(DirtyKey key, Set<DirtyReason> reasons) {
    public DirtyEntry {
        Objects.requireNonNull(key, "key");
        reasons = Set.copyOf(Objects.requireNonNull(reasons, "reasons"));
        if (reasons.isEmpty()) {
            throw new IllegalArgumentException("Dirty entry requires at least one reason");
        }
    }
}
