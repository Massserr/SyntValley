package dev.syntvalley.domain.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.syntvalley.domain.building.BuildingTemplateId;
import dev.syntvalley.domain.building.Rotation;
import dev.syntvalley.domain.building.SitePlacement;
import dev.syntvalley.domain.identity.VillageId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BuildProjectTest {
    private static final VillageId VILLAGE = new VillageId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final BuildingTemplateId TEMPLATE = new BuildingTemplateId("syntvalley:small_storehouse");
    private static final SitePlacement PLACEMENT =
            new SitePlacement("minecraft:overworld", 10, 64, -20, Rotation.NONE, 123L);

    private static BuildProject staging(int total) {
        return BuildProject.create(ProjectId.random(), VILLAGE, TEMPLATE, 1, PLACEMENT, total);
    }

    private static BuildProject changed(ProjectTransitionResult result) {
        return ((ProjectTransitionResult.Changed) result).project();
    }

    private static ProjectTransitionRejection rejection(ProjectTransitionResult result) {
        return ((ProjectTransitionResult.Rejected) result).reason();
    }

    @Test
    void createsInStaging() {
        BuildProject project = staging(26);
        assertEquals(ProjectState.STAGING, project.state());
        assertEquals(0, project.placedBlocks());
        assertEquals(26, project.totalBlocks());
        assertEquals(1, project.revision());
        assertTrue(project.pauseReason().isEmpty());
    }

    @Test
    void buildsBlockByBlockToCompletion() {
        BuildProject project = changed(staging(3).beginBuilding());
        assertEquals(ProjectState.BUILDING, project.state());
        project = changed(project.recordBlockPlaced());
        project = changed(project.recordBlockPlaced());
        assertEquals(ProjectState.BUILDING, project.state());
        project = changed(project.recordBlockPlaced());
        assertEquals(ProjectState.COMPLETED, project.state());
        assertTrue(project.isComplete());
        assertEquals(3, project.placedBlocks());
    }

    @Test
    void pauseAndResumeKeepProgress() {
        BuildProject project = changed(staging(5).beginBuilding());
        project = changed(project.recordBlockPlaced());
        project = changed(project.pause(ProjectPauseReason.SITE_OBSTRUCTED));
        assertEquals(ProjectState.PAUSED, project.state());
        assertEquals(ProjectPauseReason.SITE_OBSTRUCTED, project.pauseReason().orElseThrow());
        assertEquals(1, project.placedBlocks());

        project = changed(project.resume());
        assertEquals(ProjectState.BUILDING, project.state());
        assertTrue(project.pauseReason().isEmpty());
        assertEquals(1, project.placedBlocks());
    }

    @Test
    void cancelIsTerminalAndBlocksFurtherTransitions() {
        BuildProject project = changed(staging(5).beginBuilding());
        project = changed(project.cancel());
        assertEquals(ProjectState.CANCELLED, project.state());
        assertTrue(project.state().isTerminal());
        assertInstanceOf(ProjectTransitionResult.Rejected.class, project.cancel());
        assertInstanceOf(ProjectTransitionResult.Rejected.class, project.recordBlockPlaced());
    }

    @Test
    void rejectsOutOfOrderTransitions() {
        BuildProject stagingProject = staging(5);
        assertEquals(ProjectTransitionRejection.NOT_BUILDING, rejection(stagingProject.recordBlockPlaced()));
        assertEquals(ProjectTransitionRejection.NOT_BUILDING,
                rejection(stagingProject.pause(ProjectPauseReason.CHUNK_UNLOADED)));
        assertEquals(ProjectTransitionRejection.NOT_PAUSED, rejection(stagingProject.resume()));

        BuildProject buildingProject = changed(stagingProject.beginBuilding());
        assertEquals(ProjectTransitionRejection.NOT_STAGING, rejection(buildingProject.beginBuilding()));
    }

    @Test
    void revisionAdvancesOnEachChange() {
        BuildProject project = staging(5);
        assertEquals(1, project.revision());
        project = changed(project.beginBuilding());
        assertEquals(2, project.revision());
        project = changed(project.recordBlockPlaced());
        assertEquals(3, project.revision());
    }
}
