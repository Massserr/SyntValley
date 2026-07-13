package dev.syntvalley.domain.profession;

import dev.syntvalley.domain.task.TaskKind;
import java.util.Objects;
import java.util.Set;

/**
 * Data-driven profession definition: which work task kinds it may perform and its experience curve.
 * Loaded from data packs so new professions do not require code changes.
 */
public record ProfessionDefinition(
        ProfessionId id,
        int version,
        Set<TaskKind> workTaskKinds,
        int experiencePerLevel,
        int maxLevel
) {
    public ProfessionDefinition {
        Objects.requireNonNull(id, "id");
        workTaskKinds = Set.copyOf(Objects.requireNonNull(workTaskKinds, "workTaskKinds"));
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        if (experiencePerLevel < 1) {
            throw new IllegalArgumentException("experiencePerLevel must be positive");
        }
        if (maxLevel < 1) {
            throw new IllegalArgumentException("maxLevel must be positive");
        }
    }

    public boolean allows(TaskKind kind) {
        return workTaskKinds.contains(kind);
    }
}
