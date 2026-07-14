package dev.syntvalley.domain.project;

import dev.syntvalley.domain.building.BuildingTemplateId;
import dev.syntvalley.domain.building.SitePlacement;
import dev.syntvalley.domain.identity.VillageId;
import java.util.Objects;
import java.util.Optional;

/**
 * A build project: a chosen template placed at a validated site, advancing STAGING → BUILDING →
 * COMPLETED, with PAUSED as a recoverable stop. Coordinates are fixed at creation (Java chose them, not
 * the player) and the template version is captured so a later shape change can be detected. Progress is a
 * simple placed/total counter; the concrete block list is derived from the template and placement.
 */
public record BuildProject(
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
    public BuildProject {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(villageId, "villageId");
        Objects.requireNonNull(templateId, "templateId");
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(state, "state");
        pauseReason = Objects.requireNonNull(pauseReason, "pauseReason");

        if (templateVersion < 1) {
            throw new IllegalArgumentException("templateVersion must be positive");
        }
        if (revision < 1) {
            throw new IllegalArgumentException("revision must be positive");
        }
        if (totalBlocks < 1) {
            throw new IllegalArgumentException("totalBlocks must be positive");
        }
        if (placedBlocks < 0 || placedBlocks > totalBlocks) {
            throw new IllegalArgumentException("placedBlocks out of range");
        }
        if ((state == ProjectState.PAUSED) != pauseReason.isPresent()) {
            throw new IllegalArgumentException("pauseReason must be present exactly when PAUSED");
        }
        if (state == ProjectState.COMPLETED && placedBlocks != totalBlocks) {
            throw new IllegalArgumentException("COMPLETED requires every block placed");
        }
        if (state == ProjectState.STAGING && placedBlocks != 0) {
            throw new IllegalArgumentException("STAGING must have no placed blocks");
        }
    }

    public static BuildProject create(
            ProjectId id, VillageId villageId, BuildingTemplateId templateId,
            int templateVersion, SitePlacement placement, int totalBlocks) {
        return new BuildProject(id, villageId, templateId, templateVersion, placement,
                ProjectState.STAGING, 0, totalBlocks, Optional.empty(), 1);
    }

    public boolean isComplete() {
        return state == ProjectState.COMPLETED;
    }

    /** Materials are staged: start placing blocks. */
    public ProjectTransitionResult beginBuilding() {
        if (state != ProjectState.STAGING) {
            return new ProjectTransitionResult.Rejected(ProjectTransitionRejection.NOT_STAGING);
        }
        return changed(ProjectState.BUILDING, placedBlocks, Optional.empty());
    }

    /** One template block was placed in the world; completes the project on the last block. */
    public ProjectTransitionResult recordBlockPlaced() {
        if (state != ProjectState.BUILDING) {
            return new ProjectTransitionResult.Rejected(ProjectTransitionRejection.NOT_BUILDING);
        }
        int placed = placedBlocks + 1;
        ProjectState next = placed >= totalBlocks ? ProjectState.COMPLETED : ProjectState.BUILDING;
        return changed(next, placed, Optional.empty());
    }

    /** A recoverable stop during construction (obstruction, protection, unloaded chunk, missing stock). */
    public ProjectTransitionResult pause(ProjectPauseReason reason) {
        Objects.requireNonNull(reason, "reason");
        if (state != ProjectState.BUILDING) {
            return new ProjectTransitionResult.Rejected(ProjectTransitionRejection.NOT_BUILDING);
        }
        return changed(ProjectState.PAUSED, placedBlocks, Optional.of(reason));
    }

    public ProjectTransitionResult resume() {
        if (state != ProjectState.PAUSED) {
            return new ProjectTransitionResult.Rejected(ProjectTransitionRejection.NOT_PAUSED);
        }
        return changed(ProjectState.BUILDING, placedBlocks, Optional.empty());
    }

    public ProjectTransitionResult cancel() {
        if (state.isTerminal()) {
            return new ProjectTransitionResult.Rejected(ProjectTransitionRejection.ALREADY_TERMINAL);
        }
        return changed(ProjectState.CANCELLED, placedBlocks, Optional.empty());
    }

    private ProjectTransitionResult changed(ProjectState next, int placed, Optional<ProjectPauseReason> reason) {
        if (revision == Long.MAX_VALUE) {
            return new ProjectTransitionResult.Rejected(ProjectTransitionRejection.REVISION_EXHAUSTED);
        }
        return new ProjectTransitionResult.Changed(new BuildProject(
                id, villageId, templateId, templateVersion, placement,
                next, placed, totalBlocks, reason, revision + 1));
    }
}
