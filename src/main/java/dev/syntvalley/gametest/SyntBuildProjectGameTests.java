package dev.syntvalley.gametest;

import dev.syntvalley.application.port.WorldPlacement;
import dev.syntvalley.bootstrap.ProjectIdentity;
import dev.syntvalley.content.building.WorldPlacementAdapter;
import dev.syntvalley.domain.resource.ResourceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
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
}
