package dev.syntvalley.application.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.syntvalley.application.port.WorldPlacement;
import dev.syntvalley.domain.building.BlockOffset;
import dev.syntvalley.domain.building.BuildingTemplate;
import dev.syntvalley.domain.building.SitePlacement;
import dev.syntvalley.domain.building.TemplateBlock;
import dev.syntvalley.domain.resource.ResourceKey;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlacementPlannerTest {
    private static final BuildingTemplate STOREHOUSE = BuildingCatalog.smallStorehouse();
    private static final String DIM = "minecraft:overworld";
    private static final int CORE_X = 100;
    private static final int CORE_Y = 64;
    private static final int CORE_Z = 100;

    private final PlacementPlanner planner = PlacementPlanner.defaults();

    /** Flat world: everything buildable, solid ground just below build level. */
    private static WorldPlacement flatWorld() {
        return new WorldPlacement() {
            @Override
            public boolean isBuildable(String dimensionId, int x, int y, int z) {
                return true;
            }

            @Override
            public boolean isSolidGround(String dimensionId, int x, int y, int z) {
                return y < CORE_Y;
            }

            @Override
            public boolean placeBlock(String dimensionId, int x, int y, int z, ResourceKey block) {
                return true;
            }
        };
    }

    @Test
    void findsSiteOnFlatGround() {
        Optional<SitePlacement> plan = planner.plan(DIM, CORE_X, CORE_Y, CORE_Z, STOREHOUSE, flatWorld());
        assertTrue(plan.isPresent());

        SitePlacement placement = plan.orElseThrow();
        assertEquals(DIM, placement.dimensionId());
        assertEquals(CORE_Y, placement.originY());
        assertEquals(
                SitePlacement.planHash(DIM, placement.originX(), placement.originY(), placement.originZ(),
                        placement.rotation(), STOREHOUSE.id(), STOREHOUSE.version()),
                placement.planHash());

        WorldPlacement world = flatWorld();
        for (TemplateBlock block : STOREHOUSE.blocks()) {
            BlockOffset rotated = placement.rotation().apply(block.offset());
            assertTrue(world.isBuildable(DIM,
                    placement.originX() + rotated.x(),
                    placement.originY() + rotated.y(),
                    placement.originZ() + rotated.z()));
        }
    }

    @Test
    void noSiteWhenNothingBuildable() {
        WorldPlacement blocked = new WorldPlacement() {
            @Override
            public boolean isBuildable(String dimensionId, int x, int y, int z) {
                return false;
            }

            @Override
            public boolean isSolidGround(String dimensionId, int x, int y, int z) {
                return true;
            }

            @Override
            public boolean placeBlock(String dimensionId, int x, int y, int z, ResourceKey block) {
                return false;
            }
        };
        assertTrue(planner.plan(DIM, CORE_X, CORE_Y, CORE_Z, STOREHOUSE, blocked).isEmpty());
    }

    @Test
    void noSiteWithoutSolidFoundation() {
        WorldPlacement floating = new WorldPlacement() {
            @Override
            public boolean isBuildable(String dimensionId, int x, int y, int z) {
                return true;
            }

            @Override
            public boolean isSolidGround(String dimensionId, int x, int y, int z) {
                return false;
            }

            @Override
            public boolean placeBlock(String dimensionId, int x, int y, int z, ResourceKey block) {
                return true;
            }
        };
        assertTrue(planner.plan(DIM, CORE_X, CORE_Y, CORE_Z, STOREHOUSE, floating).isEmpty());
    }

    @Test
    void isDeterministic() {
        Optional<SitePlacement> first = planner.plan(DIM, CORE_X, CORE_Y, CORE_Z, STOREHOUSE, flatWorld());
        Optional<SitePlacement> second = planner.plan(DIM, CORE_X, CORE_Y, CORE_Z, STOREHOUSE, flatWorld());
        assertEquals(first, second);
    }

    @Test
    void skipsBlockedNearRingsAndFindsFartherSite() {
        // Buildable everywhere except a box around the core; a valid site must lie beyond it.
        WorldPlacement world = new WorldPlacement() {
            @Override
            public boolean isBuildable(String dimensionId, int x, int y, int z) {
                return Math.abs(x - CORE_X) > 3 || Math.abs(z - CORE_Z) > 3;
            }

            @Override
            public boolean isSolidGround(String dimensionId, int x, int y, int z) {
                return y < CORE_Y;
            }

            @Override
            public boolean placeBlock(String dimensionId, int x, int y, int z, ResourceKey block) {
                return true;
            }
        };
        Optional<SitePlacement> plan = planner.plan(DIM, CORE_X, CORE_Y, CORE_Z, STOREHOUSE, world);
        assertTrue(plan.isPresent());

        SitePlacement placement = plan.orElseThrow();
        for (TemplateBlock block : STOREHOUSE.blocks()) {
            BlockOffset rotated = placement.rotation().apply(block.offset());
            int x = placement.originX() + rotated.x();
            int z = placement.originZ() + rotated.z();
            assertTrue(Math.abs(x - CORE_X) > 3 || Math.abs(z - CORE_Z) > 3, "every block clears the forbidden box");
        }
    }
}
