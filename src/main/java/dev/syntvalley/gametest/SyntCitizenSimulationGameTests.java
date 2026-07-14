package dev.syntvalley.gametest;

import dev.syntvalley.application.profession.ProfessionCatalog;
import dev.syntvalley.application.service.CitizenApplicationService;
import dev.syntvalley.bootstrap.ProjectIdentity;
import dev.syntvalley.bootstrap.ServerRuntimeManager;
import dev.syntvalley.bootstrap.SyntValleyServerRuntime;
import dev.syntvalley.content.blockentity.SyntCoreBlockEntity;
import dev.syntvalley.domain.citizen.CitizenAggregate;
import dev.syntvalley.domain.identity.CitizenId;
import dev.syntvalley.domain.identity.VillageId;
import dev.syntvalley.domain.task.TaskKind;
import dev.syntvalley.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ProjectIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SyntCitizenSimulationGameTests {
    private static final BlockPos CORE_POS = BlockPos.ZERO;

    private SyntCitizenSimulationGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void needsDecayRequestFoodThenFeedingRecovers(GameTestHelper helper) {
        helper.setBlock(CORE_POS, ModBlocks.SYNT_CORE.get());
        helper.runAfterDelay(2, () -> {
            SyntValleyServerRuntime runtime = ServerRuntimeManager.getOrCreate(helper.getLevel().getServer());
            SyntCoreBlockEntity core = helper.getBlockEntity(CORE_POS);
            VillageId villageId = core.binding().orElseThrow().villageId();
            long now = helper.getLevel().getGameTime();

            CitizenApplicationService.HireResult.Hired hired =
                    (CitizenApplicationService.HireResult.Hired) runtime.hireCitizen(villageId, "Tester", now);
            CitizenId citizenId = hired.citizen().id();

            // Fast-forward the simulation by passing a far-future tick: hunger decays to critical.
            runtime.simulateCitizen(citizenId, now + 16_000);
            CitizenAggregate hungry = runtime.inspectCitizen(citizenId).orElseThrow();
            helper.assertTrue(hungry.needs().hunger() <= 200, "hunger should be critical after decay but was "
                    + hungry.needs().hunger() + " (lifecycle=" + hungry.lifecycle() + ", task="
                    + hungry.activeTask().map(t -> t.kind() + "/" + t.state()).orElse("none") + ")");
            helper.assertValueEqual(hungry.activeTask().orElseThrow().kind(), TaskKind.REQUEST_FOOD, "activity");

            helper.assertTrue(runtime.feedCitizen(citizenId, 300, now + 16_000), "feeding should apply");
            CitizenAggregate fed = runtime.inspectCitizen(citizenId).orElseThrow();
            helper.assertTrue(fed.needs().hunger() > hungry.needs().hunger(), "feeding should raise hunger");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void caretakerWorksButWandererDoesNot(GameTestHelper helper) {
        helper.setBlock(CORE_POS, ModBlocks.SYNT_CORE.get());
        helper.runAfterDelay(2, () -> {
            SyntValleyServerRuntime runtime = ServerRuntimeManager.getOrCreate(helper.getLevel().getServer());
            SyntCoreBlockEntity core = helper.getBlockEntity(CORE_POS);
            VillageId villageId = core.binding().orElseThrow().villageId();
            long now = helper.getLevel().getGameTime();

            CitizenApplicationService.HireResult.Hired hired =
                    (CitizenApplicationService.HireResult.Hired) runtime.hireCitizen(villageId, "Worker", now);
            CitizenId citizenId = hired.citizen().id();

            // Default caretaker: a calm citizen starts a WORK shift and earns experience on completion.
            runtime.simulateCitizen(citizenId, now + 100);
            CitizenAggregate working = runtime.inspectCitizen(citizenId).orElseThrow();
            helper.assertValueEqual(working.activeTask().orElseThrow().kind(), TaskKind.WORK, "caretaker works");

            runtime.simulateCitizen(citizenId, now + 100 + 200);
            CitizenAggregate afterShift = runtime.inspectCitizen(citizenId).orElseThrow();
            helper.assertTrue(afterShift.profession().orElseThrow().experience() > 0, "shift granted experience");

            // Reassigned to a non-work profession: it no longer works.
            helper.assertTrue(runtime.assignProfession(citizenId, ProfessionCatalog.WANDERER, now + 400),
                    "assign wanderer");
            runtime.simulateCitizen(citizenId, now + 500);
            CitizenAggregate wanderer = runtime.inspectCitizen(citizenId).orElseThrow();
            helper.assertTrue(wanderer.activeTask().orElseThrow().kind() != TaskKind.WORK, "wanderer does not work");
            helper.succeed();
        });
    }
}
