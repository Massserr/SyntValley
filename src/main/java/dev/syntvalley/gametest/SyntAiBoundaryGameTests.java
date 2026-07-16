package dev.syntvalley.gametest;

import dev.syntvalley.bootstrap.ProjectIdentity;
import dev.syntvalley.bootstrap.ServerRuntimeManager;
import dev.syntvalley.bootstrap.SyntValleyServerRuntime;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ProjectIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SyntAiBoundaryGameTests {
    private SyntAiBoundaryGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void disabledBackendReportsStatusAndRejectsPing(GameTestHelper helper) {
        SyntValleyServerRuntime runtime = ServerRuntimeManager.getOrCreate(helper.getLevel().getServer());
        // AI is disabled by default: the server runs fully deterministically and says so.
        helper.assertTrue(runtime.aiStatus().contains("disabled"),
                "expected a disabled backend status but was: " + runtime.aiStatus());
        helper.assertTrue(runtime.aiPing().contains("disabled"),
                "a ping with a disabled backend reports it instead of queueing");
        helper.succeed();
    }
}
