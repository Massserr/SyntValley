package dev.syntvalley.gametest;

import dev.syntvalley.application.service.CitizenApplicationService;
import dev.syntvalley.bootstrap.ProjectIdentity;
import dev.syntvalley.bootstrap.ServerRuntimeManager;
import dev.syntvalley.bootstrap.SyntValleyServerRuntime;
import dev.syntvalley.content.blockentity.SyntCoreBlockEntity;
import dev.syntvalley.content.entity.SyntCitizenEntity;
import dev.syntvalley.domain.citizen.CitizenAggregate;
import dev.syntvalley.domain.citizen.CitizenLifecycle;
import dev.syntvalley.domain.identity.CitizenId;
import dev.syntvalley.domain.village.CoreBinding;
import dev.syntvalley.registry.ModBlocks;
import dev.syntvalley.registry.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ProjectIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SyntCitizenGameTests {
    private static final BlockPos CORE_POS = BlockPos.ZERO;
    private static final BlockPos SPAWN_POS = new BlockPos(0, 2, 0);

    private SyntCitizenGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void hireSpawnsBoundCitizen(GameTestHelper helper) {
        helper.setBlock(CORE_POS, ModBlocks.SYNT_CORE.get());
        helper.runAfterDelay(2, () -> {
            SyntValleyServerRuntime runtime = ServerRuntimeManager.getOrCreate(helper.getLevel().getServer());
            SyntCitizenEntity entity = hireAndSpawn(helper, runtime);
            helper.runAfterDelay(3, () -> {
                CitizenId citizenId = entity.binding().orElseThrow().citizenId();
                CitizenAggregate citizen = runtime.inspectCitizen(citizenId).orElseThrow();
                helper.assertValueEqual(citizen.lifecycle(), CitizenLifecycle.ACTIVE, "citizen lifecycle");
                helper.assertValueEqual(citizen.boundEntityId().orElseThrow(), entity.getUUID(), "bound entity id");
                helper.assertTrue(!entity.hasInvalidBinding(), "entity binding should be valid");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void citizenDeathTransitionsToDeceased(GameTestHelper helper) {
        helper.setBlock(CORE_POS, ModBlocks.SYNT_CORE.get());
        helper.runAfterDelay(2, () -> {
            SyntValleyServerRuntime runtime = ServerRuntimeManager.getOrCreate(helper.getLevel().getServer());
            SyntCitizenEntity entity = hireAndSpawn(helper, runtime);
            helper.runAfterDelay(3, () -> {
                CitizenId citizenId = entity.binding().orElseThrow().citizenId();
                entity.kill();
                helper.runAfterDelay(2, () -> {
                    CitizenAggregate citizen = runtime.inspectCitizen(citizenId).orElseThrow();
                    helper.assertValueEqual(citizen.lifecycle(), CitizenLifecycle.DECEASED, "citizen lifecycle after death");
                    helper.assertTrue(citizen.boundEntityId().isEmpty(), "deceased citizen should not retain a bound entity");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void duplicateCitizenEntityIsQuarantined(GameTestHelper helper) {
        helper.setBlock(CORE_POS, ModBlocks.SYNT_CORE.get());
        helper.runAfterDelay(2, () -> {
            SyntValleyServerRuntime runtime = ServerRuntimeManager.getOrCreate(helper.getLevel().getServer());
            SyntCitizenEntity original = hireAndSpawn(helper, runtime);
            helper.runAfterDelay(3, () -> {
                SyntCitizenEntity duplicate = ModEntityTypes.SYNT_CITIZEN.get().create(helper.getLevel());
                duplicate.applyBinding(original.binding().orElseThrow());
                BlockPos absolute = helper.absolutePos(SPAWN_POS);
                duplicate.moveTo(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5, 0.0F, 0.0F);
                helper.getLevel().addFreshEntity(duplicate);
                helper.runAfterDelay(3, () -> {
                    CitizenId citizenId = original.binding().orElseThrow().citizenId();
                    CitizenAggregate citizen = runtime.inspectCitizen(citizenId).orElseThrow();
                    helper.assertValueEqual(citizen.boundEntityId().orElseThrow(), original.getUUID(), "bound entity remains original");
                    helper.assertTrue(duplicate.isRemoved(), "duplicate entity should be quarantined");
                    helper.succeed();
                });
            });
        });
    }

    private static SyntCitizenEntity hireAndSpawn(GameTestHelper helper, SyntValleyServerRuntime runtime) {
        SyntCoreBlockEntity core = helper.getBlockEntity(CORE_POS);
        CoreBinding coreBinding = core.binding().orElseThrow();
        CitizenApplicationService.HireResult result =
                runtime.hireCitizen(coreBinding.villageId(), "Tester", helper.getLevel().getGameTime());
        CitizenApplicationService.HireResult.Hired hired = (CitizenApplicationService.HireResult.Hired) result;

        SyntCitizenEntity entity = ModEntityTypes.SYNT_CITIZEN.get().create(helper.getLevel());
        entity.applyBinding(hired.citizen().entityBinding());
        BlockPos absolute = helper.absolutePos(SPAWN_POS);
        entity.moveTo(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5, 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }
}
