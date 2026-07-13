package dev.syntvalley.gametest;

import dev.syntvalley.bootstrap.ProjectIdentity;
import dev.syntvalley.bootstrap.ServerRuntimeManager;
import dev.syntvalley.bootstrap.SyntValleyServerRuntime;
import dev.syntvalley.content.blockentity.SyntCoreBlockEntity;
import dev.syntvalley.domain.identity.VillageId;
import dev.syntvalley.registry.ModBlocks;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ProjectIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SyntVillageConsoleGameTests {
    private static final BlockPos CORE_POS = BlockPos.ZERO;

    private SyntVillageConsoleGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void coreSelectionBindsConsoleOnce(GameTestHelper helper) {
        helper.setBlock(CORE_POS, ModBlocks.SYNT_CORE.get());
        helper.runAfterDelay(2, () -> {
            SyntValleyServerRuntime runtime = ServerRuntimeManager.getOrCreate(helper.getLevel().getServer());
            SyntCoreBlockEntity core = helper.getBlockEntity(CORE_POS);
            VillageId villageId = core.binding().orElseThrow().villageId();
            UUID player = UUID.randomUUID();

            runtime.selectVillageForLink(player, villageId, helper.getLevel().getGameTime());
            VillageId bound = runtime.bindConsole(player, helper.getLevel().getGameTime()).orElseThrow();
            helper.assertValueEqual(bound, villageId, "console-bound Village");
            helper.assertTrue(
                    runtime.bindConsole(player, helper.getLevel().getGameTime()).isEmpty(),
                    "selection must be consumed exactly once"
            );
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void bindWithoutSelectionFails(GameTestHelper helper) {
        SyntValleyServerRuntime runtime = ServerRuntimeManager.getOrCreate(helper.getLevel().getServer());
        helper.assertTrue(
                runtime.bindConsole(UUID.randomUUID(), helper.getLevel().getGameTime()).isEmpty(),
                "binding without a prior selection must fail"
        );
        helper.succeed();
    }
}
