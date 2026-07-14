package dev.syntvalley.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.syntvalley.application.building.BuildingCatalog;
import dev.syntvalley.application.building.PlacementPlanner;
import dev.syntvalley.application.port.ResourceWithdrawal;
import dev.syntvalley.application.port.WorldPlacement;
import dev.syntvalley.domain.building.BuildingTemplate;
import dev.syntvalley.domain.building.BuildingTemplateId;
import dev.syntvalley.domain.identity.VillageId;
import dev.syntvalley.domain.project.BuildProject;
import dev.syntvalley.domain.project.ProjectId;
import dev.syntvalley.domain.project.ProjectPauseReason;
import dev.syntvalley.domain.project.ProjectState;
import dev.syntvalley.domain.resource.ResourceKey;
import dev.syntvalley.domain.resource.ResourceLedger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectApplicationServiceTest {
    private static final VillageId VILLAGE = new VillageId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
    private static final String DIM = "minecraft:overworld";
    private static final BuildingTemplate STOREHOUSE = BuildingCatalog.smallStorehouse();

    private final ProjectApplicationService service =
            new ProjectApplicationService(PlacementPlanner.defaults(), ProjectId::random);

    private static final class FakeWorld implements WorldPlacement {
        private boolean buildable = true;
        private final Set<String> placed = new HashSet<>();

        @Override
        public boolean isBuildable(String dimensionId, int x, int y, int z) {
            return buildable;
        }

        @Override
        public boolean isSolidGround(String dimensionId, int x, int y, int z) {
            return y < 64;
        }

        @Override
        public boolean placeBlock(String dimensionId, int x, int y, int z, ResourceKey block) {
            placed.add(x + "," + y + "," + z);
            return true;
        }
    }

    private static final class FakeStock implements ResourceWithdrawal {
        private final Map<ResourceKey, Integer> stock = new HashMap<>();

        @Override
        public int withdraw(ResourceKey key, int amount) {
            int have = stock.getOrDefault(key, 0);
            int given = Math.min(have, amount);
            stock.put(key, have - given);
            return given;
        }

        int total() {
            return stock.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    private static ResourceLedger fullBillLedger() {
        return new ResourceLedger(STOREHOUSE.bill(), 0L);
    }

    private static FakeStock fullStock() {
        FakeStock stock = new FakeStock();
        stock.stock.putAll(STOREHOUSE.bill());
        return stock;
    }

    private BuildProject proposedProject(FakeWorld world) {
        ProposeResult result = service.propose(VILLAGE, DIM, 0, 64, 0, BuildingCatalog.SMALL_STOREHOUSE, world);
        return ((ProposeResult.Proposed) result).project();
    }

    private BuildProject buildingProject(FakeWorld world) {
        return service.advance(proposedProject(world), fullBillLedger(), fullStock(), world);
    }

    @Test
    void rejectsUnknownTemplate() {
        ProposeResult result =
                service.propose(VILLAGE, DIM, 0, 64, 0, new BuildingTemplateId("syntvalley:unknown"), new FakeWorld());
        assertEquals(ProposalRejection.UNKNOWN_TEMPLATE, ((ProposeResult.Rejected) result).reason());
    }

    @Test
    void rejectsWhenNoBuildableSite() {
        FakeWorld world = new FakeWorld();
        world.buildable = false;
        ProposeResult result = service.propose(VILLAGE, DIM, 0, 64, 0, BuildingCatalog.SMALL_STOREHOUSE, world);
        assertEquals(ProposalRejection.NO_SITE, ((ProposeResult.Rejected) result).reason());
    }

    @Test
    void staysStagingUntilBillIsDelivered() {
        FakeWorld world = new FakeWorld();
        BuildProject project = proposedProject(world);
        project = service.advance(project, new ResourceLedger(Map.of(), 0L), new FakeStock(), world);
        assertEquals(ProjectState.STAGING, project.state());
    }

    @Test
    void proposesThenStagesThenBuildsToCompletion() {
        FakeWorld world = new FakeWorld();
        BuildProject project = proposedProject(world);
        assertEquals(ProjectState.STAGING, project.state());

        FakeStock stock = fullStock();
        project = service.advance(project, fullBillLedger(), stock, world);
        assertEquals(ProjectState.BUILDING, project.state());

        int guard = 0;
        while (!project.isComplete() && guard++ < 200) {
            project = service.advance(project, fullBillLedger(), stock, world);
        }

        assertEquals(ProjectState.COMPLETED, project.state());
        assertEquals(STOREHOUSE.blockCount(), project.placedBlocks());
        assertEquals(STOREHOUSE.blockCount(), world.placed.size());
        assertEquals(0, stock.total(), "the whole bill is consumed, one block at a time");
    }

    @Test
    void pausesOnObstructionThenResumesWhenCleared() {
        FakeWorld world = new FakeWorld();
        BuildProject project = buildingProject(world);
        assertEquals(ProjectState.BUILDING, project.state());

        world.buildable = false;
        project = service.advance(project, fullBillLedger(), fullStock(), world);
        assertEquals(ProjectState.PAUSED, project.state());
        assertEquals(ProjectPauseReason.SITE_OBSTRUCTED, project.pauseReason().orElseThrow());

        world.buildable = true;
        project = service.advance(project, fullBillLedger(), fullStock(), world);
        assertEquals(ProjectState.BUILDING, project.state());
        assertTrue(project.pauseReason().isEmpty());
    }

    @Test
    void pausesWhenMaterialsAreMissingMidBuild() {
        FakeWorld world = new FakeWorld();
        BuildProject project = buildingProject(world);
        project = service.advance(project, fullBillLedger(), new FakeStock(), world);
        assertEquals(ProjectState.PAUSED, project.state());
        assertEquals(ProjectPauseReason.MISSING_MATERIALS, project.pauseReason().orElseThrow());
    }
}
