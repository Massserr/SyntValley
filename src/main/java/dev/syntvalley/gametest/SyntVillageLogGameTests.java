package dev.syntvalley.gametest;

import dev.syntvalley.application.service.CitizenApplicationService;
import dev.syntvalley.bootstrap.ProjectIdentity;
import dev.syntvalley.bootstrap.ServerRuntimeManager;
import dev.syntvalley.bootstrap.SyntValleyServerRuntime;
import dev.syntvalley.content.blockentity.SyntCoreBlockEntity;
import dev.syntvalley.domain.decision.DecisionRecord;
import dev.syntvalley.domain.identity.CitizenId;
import dev.syntvalley.domain.identity.VillageId;
import dev.syntvalley.domain.memory.MemoryKind;
import dev.syntvalley.domain.memory.MemoryRecord;
import dev.syntvalley.registry.ModBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ProjectIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SyntVillageLogGameTests {
    private static final BlockPos CORE_POS = BlockPos.ZERO;

    private SyntVillageLogGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void feedingIsRememberedOnceAndTaskChangesAreAudited(GameTestHelper helper) {
        helper.setBlock(CORE_POS, ModBlocks.SYNT_CORE.get());
        helper.runAfterDelay(2, () -> {
            SyntValleyServerRuntime runtime = ServerRuntimeManager.getOrCreate(helper.getLevel().getServer());
            SyntCoreBlockEntity core = helper.getBlockEntity(CORE_POS);
            VillageId villageId = core.binding().orElseThrow().villageId();
            long now = helper.getLevel().getGameTime();

            CitizenApplicationService.HireResult.Hired hired =
                    (CitizenApplicationService.HireResult.Hired) runtime.hireCitizen(villageId, "Chronicler", now);
            CitizenId citizenId = hired.citizen().id();

            // The same feed event replayed at the same tick must be remembered exactly once.
            helper.assertTrue(runtime.feedCitizen(citizenId, 100, now + 10), "first feed applies");
            helper.assertTrue(runtime.feedCitizen(citizenId, 100, now + 10), "replayed feed still applies");
            List<MemoryRecord> memories = runtime.villageMemories(villageId);
            long fedCount = memories.stream().filter(m -> m.kind() == MemoryKind.PLAYER_FED_CITIZEN
                    && m.dedupeKey().endsWith(":" + (now + 10))).count();
            helper.assertValueEqual((int) fedCount, 1, "feed memories for the same event");

            // A far-future step flips the citizen to REQUEST_FOOD; the planner outcome is audited.
            runtime.simulateCitizen(citizenId, now + 16_000);
            List<DecisionRecord> decisions = runtime.villageDecisions(villageId, 16);
            helper.assertTrue(decisions.stream().anyMatch(d -> "REQUEST_FOOD".equals(d.chosen())),
                    "the hunger decision is in the audit log but decisions were " + decisions);
            helper.succeed();
        });
    }
}
