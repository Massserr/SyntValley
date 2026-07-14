package dev.syntvalley.application.service;

import dev.syntvalley.application.building.BuildingCatalog;
import dev.syntvalley.application.building.PlacementPlanner;
import dev.syntvalley.application.port.ResourceWithdrawal;
import dev.syntvalley.application.port.WorldPlacement;
import dev.syntvalley.domain.building.BlockOffset;
import dev.syntvalley.domain.building.BuildingTemplate;
import dev.syntvalley.domain.building.BuildingTemplateId;
import dev.syntvalley.domain.building.SitePlacement;
import dev.syntvalley.domain.building.TemplateBlock;
import dev.syntvalley.domain.identity.VillageId;
import dev.syntvalley.domain.project.BuildProject;
import dev.syntvalley.domain.project.ProjectId;
import dev.syntvalley.domain.project.ProjectPauseReason;
import dev.syntvalley.domain.project.ProjectState;
import dev.syntvalley.domain.project.ProjectTransitionResult;
import dev.syntvalley.domain.resource.ResourceKey;
import dev.syntvalley.domain.resource.ResourceLedger;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Orchestrates the build project lifecycle. {@link #propose} lets Java pick and validate a site and
 * admit a project; {@link #advance} drives one bounded step: gather the bill from village storage, then
 * place a single block — re-checking the world immediately before each placement and pausing (never
 * overwriting) on obstruction, missing stock, or a template that changed shape. Materials are drawn one
 * block at a time so a restart resumes from the persisted progress without paying twice.
 */
public final class ProjectApplicationService {
    private final PlacementPlanner placementPlanner;
    private final Supplier<ProjectId> idFactory;

    public ProjectApplicationService(PlacementPlanner placementPlanner, Supplier<ProjectId> idFactory) {
        this.placementPlanner = Objects.requireNonNull(placementPlanner, "placementPlanner");
        this.idFactory = Objects.requireNonNull(idFactory, "idFactory");
    }

    public ProposeResult propose(
            VillageId villageId, String dimensionId, int coreX, int coreY, int coreZ,
            BuildingTemplateId templateId, WorldPlacement world) {
        Objects.requireNonNull(villageId, "villageId");
        Objects.requireNonNull(templateId, "templateId");
        Objects.requireNonNull(world, "world");
        Optional<BuildingTemplate> template = BuildingCatalog.get(templateId);
        if (template.isEmpty()) {
            return new ProposeResult.Rejected(ProposalRejection.UNKNOWN_TEMPLATE);
        }
        BuildingTemplate definition = template.orElseThrow();
        Optional<SitePlacement> placement =
                placementPlanner.plan(dimensionId, coreX, coreY, coreZ, definition, world);
        if (placement.isEmpty()) {
            return new ProposeResult.Rejected(ProposalRejection.NO_SITE);
        }
        return new ProposeResult.Proposed(BuildProject.create(
                idFactory.get(), villageId, templateId, definition.version(),
                placement.orElseThrow(), definition.blockCount()));
    }

    /** Advances a project by one bounded step, applying world/material effects and returning its new state. */
    public BuildProject advance(
            BuildProject project, ResourceLedger ledger, ResourceWithdrawal withdrawal, WorldPlacement world) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(ledger, "ledger");
        Objects.requireNonNull(withdrawal, "withdrawal");
        Objects.requireNonNull(world, "world");
        if (project.state().isTerminal()) {
            return project;
        }
        Optional<BuildingTemplate> found = BuildingCatalog.get(project.templateId());
        if (found.isEmpty() || found.orElseThrow().version() != project.templateVersion()) {
            return project.state() == ProjectState.BUILDING
                    ? changedOrSame(project.pause(ProjectPauseReason.TEMPLATE_VERSION_MISMATCH), project)
                    : project;
        }
        BuildingTemplate template = found.orElseThrow();
        return switch (project.state()) {
            case STAGING -> advanceStaging(project, template, ledger);
            case BUILDING -> advanceBuilding(project, template, withdrawal, world);
            case PAUSED -> advancePaused(project, template, world);
            default -> project;
        };
    }

    private BuildProject advanceStaging(BuildProject project, BuildingTemplate template, ResourceLedger ledger) {
        for (Map.Entry<ResourceKey, Integer> entry : template.bill().entrySet()) {
            if (ledger.count(entry.getKey()) < entry.getValue()) {
                return project; // still waiting for the full bill to be delivered
            }
        }
        return changedOrSame(project.beginBuilding(), project);
    }

    private BuildProject advanceBuilding(
            BuildProject project, BuildingTemplate template, ResourceWithdrawal withdrawal, WorldPlacement world) {
        List<TemplateBlock> order = template.placementOrder();
        if (project.placedBlocks() >= order.size()) {
            return project;
        }
        TemplateBlock next = order.get(project.placedBlocks());
        SitePlacement site = project.placement();
        BlockOffset rotated = site.rotation().apply(next.offset());
        int x = site.originX() + rotated.x();
        int y = site.originY() + rotated.y();
        int z = site.originZ() + rotated.z();

        if (!world.isBuildable(site.dimensionId(), x, y, z)) {
            return changedOrSame(project.pause(ProjectPauseReason.SITE_OBSTRUCTED), project);
        }
        if (withdrawal.withdraw(next.block(), 1) < 1) {
            return changedOrSame(project.pause(ProjectPauseReason.MISSING_MATERIALS), project);
        }
        if (!world.placeBlock(site.dimensionId(), x, y, z, next.block())) {
            return changedOrSame(project.pause(ProjectPauseReason.SITE_OBSTRUCTED), project);
        }
        return changedOrSame(project.recordBlockPlaced(), project);
    }

    private BuildProject advancePaused(BuildProject project, BuildingTemplate template, WorldPlacement world) {
        List<TemplateBlock> order = template.placementOrder();
        if (project.placedBlocks() >= order.size()) {
            return project;
        }
        TemplateBlock next = order.get(project.placedBlocks());
        SitePlacement site = project.placement();
        BlockOffset rotated = site.rotation().apply(next.offset());
        int x = site.originX() + rotated.x();
        int y = site.originY() + rotated.y();
        int z = site.originZ() + rotated.z();
        if (world.isBuildable(site.dimensionId(), x, y, z)) {
            return changedOrSame(project.resume(), project);
        }
        return project;
    }

    private static BuildProject changedOrSame(ProjectTransitionResult result, BuildProject fallback) {
        return result instanceof ProjectTransitionResult.Changed changed ? changed.project() : fallback;
    }
}
