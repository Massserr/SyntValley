package dev.syntvalley.gametest;

import dev.syntvalley.bootstrap.ProjectIdentity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Minimal integration smoke test proving that the mod loads in the headless Game Test server.
 */
@GameTestHolder(ProjectIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BootstrapGameTests {
    private BootstrapGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void modLoads(GameTestHelper helper) {
        helper.succeed();
    }
}
