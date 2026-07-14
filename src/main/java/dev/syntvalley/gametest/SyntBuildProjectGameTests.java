package dev.syntvalley.gametest;

import dev.syntvalley.application.building.BuildingCatalog;
import dev.syntvalley.application.port.WorldPlacement;
import dev.syntvalley.application.service.ProposeResult;
import dev.syntvalley.bootstrap.ProjectIdentity;
import dev.syntvalley.bootstrap.ServerRuntimeManager;
import dev.syntvalley.bootstrap.SyntValleyServerRuntime;
import dev.syntvalley.content.blockentity.SyntCoreBlockEntity;
import dev.syntvalley.content.blockentity.VillageStorageBlockEntity;
import dev.syntvalley.content.building.WorldPlacementAdapter;
import dev.syntvalley.domain.identity.VillageId;
import dev.syntvalley.domain.project.BuildProject;
import dev.syntvalley.domain.project.ProjectId;
import dev.syntvalley.domain.project.ProjectState;
import dev.syntvalley.domain.resource.ResourceKey;
import dev.syntvalley.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ProjectIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SyntBuildProjectGameTests {
    private SyntBuildProjectGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void worldAdapterReportsGroundClearanceAndPlaces(GameTestHelper helper) {
        // Set up our own known state so the test never depends on a clean shared world.
        helper.setBlock(new BlockPos(1, 0, 0), Blocks.STONE);
        helper.setBlock(new BlockPos(1, 1, 0), Blocks.AIR);
        helper.setBlock(new BlockPos(1, 2, 0), Blocks.AIR);
        helper.runAfterDelay(2, () -> {
            ServerLevel level = helper.getLevel();
            WorldPlacement world = new WorldPlacementAdapter(level);
            String dim = level.dimension().location().toString();
            BlockPos ground = helper.absolutePos(new BlockPos(1, 0, 0));
            BlockPos above = ground.above();

            helper.assertTrue(world.isSolidGround(dim, ground.getX(), ground.getY(), ground.getZ()),
                    "stone is solid ground");
            helper.assertTrue(!world.isBuildable(dim, ground.getX(), ground.getY(), ground.getZ()),
                    "solid stone is not buildable");
            helper.assertTrue(world.isBuildable(dim, above.getX(), above.getY(), above.getZ()),
                    "clear space above is buildable");

            helper.assertTrue(world.placeBlock(dim, above.getX(), above.getY(), above.getZ(),
                    new ResourceKey("minecraft:oak_planks")), "places a plank");
            helper.assertTrue(level.getBlockState(above).is(Blocks.OAK_PLANKS), "the plank is in the world");

            helper.assertTrue(!world.placeBlock(dim, above.getX(), above.getY() + 1, above.getZ(),
                    new ResourceKey("syntvalley:does_not_exist")), "an unknown block id is refused, not guessed");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void ordersStagesAndBuildsTheShelter(GameTestHelper helper) {
        // Core is raised to y=1 so its site sits on a y=0 stone pad (no negative-Y placement).
        helper.setBlock(new BlockPos(0, 1, 0), ModBlocks.SYNT_CORE.get());
        helper.setBlock(new BlockPos(1, 1, 0), ModBlocks.VILLAGE_STORAGE.get());
        // A 3x3 stone pad with a cleared column above, exactly at the planner's first valid site.
        for (int x = 2; x <= 4; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                for (int y = 1; y <= 4; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }

        helper.runAfterDelay(2, () -> {
            ServerLevel level = helper.getLevel();
            SyntValleyServerRuntime runtime = ServerRuntimeManager.getOrCreate(level.getServer());
            SyntCoreBlockEntity core = helper.getBlockEntity(new BlockPos(0, 1, 0));
            VillageId villageId = core.binding().orElseThrow().villageId();

            VillageStorageBlockEntity storage = helper.getBlockEntity(new BlockPos(1, 1, 0));
            storage.bindToVillage(level, villageId);
            storage.deposit(new ItemStack(Items.OAK_PLANKS, 32));
            storage.deposit(new ItemStack(Items.OAK_FENCE, 16));

            ProposeResult result = runtime.proposeProject(
                    level, villageId, helper.absolutePos(new BlockPos(0, 1, 0)), BuildingCatalog.SMALL_STOREHOUSE);
            helper.assertTrue(result instanceof ProposeResult.Proposed,
                    "expected a site to be found but got " + result);
            ProjectId projectId = ((ProposeResult.Proposed) result).project().id();

            long now = level.getGameTime();
            for (int step = 0; step < 60; step++) {
                runtime.advanceProject(projectId, now + step);
            }

            BuildProject project = runtime.inspectProject(projectId).orElseThrow();
            helper.assertTrue(project.state() == ProjectState.COMPLETED,
                    "expected COMPLETED but was " + project.state() + " placed=" + project.placedBlocks()
                            + "/" + project.totalBlocks()
                            + " pause=" + project.pauseReason().map(Enum::name).orElse("none"));
            helper.assertValueEqual(project.placedBlocks(), 26, "placed blocks");
            helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(2, 1, 0))).is(Blocks.OAK_PLANKS),
                    "the shelter floor is placed in the world");
            helper.succeed();
        });
    }
}
