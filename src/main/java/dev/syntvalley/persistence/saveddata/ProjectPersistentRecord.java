package dev.syntvalley.persistence.saveddata;

import dev.syntvalley.domain.building.BuildingTemplateId;
import dev.syntvalley.domain.building.SitePlacement;
import dev.syntvalley.domain.identity.VillageId;
import dev.syntvalley.domain.project.BuildProject;
import dev.syntvalley.domain.project.ProjectId;
import dev.syntvalley.domain.project.ProjectPauseReason;
import dev.syntvalley.domain.project.ProjectState;
import java.util.Objects;
import java.util.Optional;

/** Schema-1 build project record (Slice 8). Reuses the domain aggregate for validation. */
public record ProjectPersistentRecord(
        ProjectId id,
        VillageId villageId,
        BuildingTemplateId templateId,
        int templateVersion,
        SitePlacement placement,
        ProjectState state,
        int placedBlocks,
        int totalBlocks,
        Optional<ProjectPauseReason> pauseReason,
        long revision
) {
    public ProjectPersistentRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(villageId, "villageId");
        Objects.requireNonNull(templateId, "templateId");
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(state, "state");
        pauseReason = Objects.requireNonNull(pauseReason, "pauseReason");
        // Reuse domain validation for the full aggregate shape.
        new BuildProject(id, villageId, templateId, templateVersion, placement, state,
                placedBlocks, totalBlocks, pauseReason, revision);
    }

    public static ProjectPersistentRecord fromAggregate(BuildProject project) {
        Objects.requireNonNull(project, "project");
        return new ProjectPersistentRecord(
                project.id(), project.villageId(), project.templateId(), project.templateVersion(),
                project.placement(), project.state(), project.placedBlocks(), project.totalBlocks(),
                project.pauseReason(), project.revision());
    }

    public ProjectPersistentRecord withAggregate(BuildProject project) {
        Objects.requireNonNull(project, "project");
        if (!id.equals(project.id())) {
            throw new IllegalArgumentException("Cannot replace a project record with another ID");
        }
        return fromAggregate(project);
    }

    public BuildProject toAggregate() {
        return new BuildProject(id, villageId, templateId, templateVersion, placement, state,
                placedBlocks, totalBlocks, pauseReason, revision);
    }
}
