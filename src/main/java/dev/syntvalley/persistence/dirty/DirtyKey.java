package dev.syntvalley.persistence.dirty;

import dev.syntvalley.domain.identity.CitizenId;
import dev.syntvalley.domain.identity.VillageId;
import dev.syntvalley.domain.project.ProjectId;
import java.util.Objects;
import java.util.UUID;

/** Opaque bookkeeping key. {@link DirtyKind} disambiguates identifiers that share the UUID space. */
public record DirtyKey(DirtyKind kind, UUID id) {
    public DirtyKey {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
    }

    public static DirtyKey village(VillageId villageId) {
        return new DirtyKey(DirtyKind.VILLAGE, Objects.requireNonNull(villageId, "villageId").value());
    }

    public static DirtyKey citizen(CitizenId citizenId) {
        return new DirtyKey(DirtyKind.CITIZEN, Objects.requireNonNull(citizenId, "citizenId").value());
    }

    public static DirtyKey project(ProjectId projectId) {
        return new DirtyKey(DirtyKind.PROJECT, Objects.requireNonNull(projectId, "projectId").value());
    }

    public static DirtyKey memory(VillageId villageId) {
        return new DirtyKey(DirtyKind.MEMORY, Objects.requireNonNull(villageId, "villageId").value());
    }

    public static DirtyKey decision(VillageId villageId) {
        return new DirtyKey(DirtyKind.DECISION, Objects.requireNonNull(villageId, "villageId").value());
    }
}
