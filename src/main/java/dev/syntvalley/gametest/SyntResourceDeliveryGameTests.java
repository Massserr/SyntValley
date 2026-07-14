package dev.syntvalley.gametest;

import dev.syntvalley.application.service.CitizenApplicationService;
import dev.syntvalley.bootstrap.ProjectIdentity;
import dev.syntvalley.bootstrap.ServerRuntimeManager;
import dev.syntvalley.bootstrap.SyntValleyServerRuntime;
import dev.syntvalley.content.blockentity.SyntCoreBlockEntity;
import dev.syntvalley.content.blockentity.VillageStorageBlockEntity;
import dev.syntvalley.domain.citizen.CitizenAggregate;
import dev.syntvalley.domain.identity.CitizenId;
import dev.syntvalley.domain.identity.VillageId;
import dev.syntvalley.domain.resource.ResourceKey;
import dev.syntvalley.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ProjectIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SyntResourceDeliveryGameTests {
    private static final BlockPos CORE_POS = BlockPos.ZERO;
    private static final BlockPos STORAGE_POS = new BlockPos(1, 0, 0);
    private static final ResourceKey BREAD = new ResourceKey("minecraft:bread");

    private SyntResourceDeliveryGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void hungryCitizenEatsFromLinkedStorage(GameTestHelper helper) {
        helper.setBlock(CORE_POS, ModBlocks.SYNT_CORE.get());
        helper.setBlock(STORAGE_POS, ModBlocks.VILLAGE_STORAGE.get());
        helper.runAfterDelay(2, () -> {
            ServerLevel level = helper.getLevel();
            SyntValleyServerRuntime runtime = ServerRuntimeManager.getOrCreate(level.getServer());
            SyntCoreBlockEntity core = helper.getBlockEntity(CORE_POS);
            VillageId villageId = core.binding().orElseThrow().villageId();
            long now = level.getGameTime();

            VillageStorageBlockEntity storage = helper.getBlockEntity(STORAGE_POS);
            storage.bindToVillage(level, villageId);
            storage.deposit(new ItemStack(Items.BREAD, 3));
            helper.assertValueEqual(breadCount(storage), 3, "stocked bread");

            CitizenApplicationService.HireResult.Hired hired =
                    (CitizenApplicationService.HireResult.Hired) runtime.hireCitizen(villageId, "Eater", now);
            CitizenId citizenId = hired.citizen().id();

            // Far-future tick: hunger decays to critical, the citizen requests food and eats it in one step.
            runtime.simulateCitizen(citizenId, now + 16_000);

            CitizenAggregate fed = runtime.inspectCitizen(citizenId).orElseThrow();
            helper.assertTrue(fed.needs().hunger() > 200, "eating from storage lifts hunger out of critical");
            helper.assertValueEqual(breadCount(storage), 2, "exactly one loaf consumed");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void unlinkedStorageIsIgnored(GameTestHelper helper) {
        helper.setBlock(CORE_POS, ModBlocks.SYNT_CORE.get());
        helper.setBlock(STORAGE_POS, ModBlocks.VILLAGE_STORAGE.get());
        helper.runAfterDelay(2, () -> {
            ServerLevel level = helper.getLevel();
            SyntValleyServerRuntime runtime = ServerRuntimeManager.getOrCreate(level.getServer());
            SyntCoreBlockEntity core = helper.getBlockEntity(CORE_POS);
            VillageId villageId = core.binding().orElseThrow().villageId();
            long now = level.getGameTime();

            // Stocked but never linked to the village: it must not be counted or drawn from.
            VillageStorageBlockEntity storage = helper.getBlockEntity(STORAGE_POS);
            storage.deposit(new ItemStack(Items.BREAD, 3));

            CitizenApplicationService.HireResult.Hired hired =
                    (CitizenApplicationService.HireResult.Hired) runtime.hireCitizen(villageId, "Eater", now);
            CitizenId citizenId = hired.citizen().id();

            runtime.simulateCitizen(citizenId, now + 16_000);

            CitizenAggregate hungry = runtime.inspectCitizen(citizenId).orElseThrow();
            helper.assertTrue(hungry.needs().hunger() <= 200, "no linked food should leave hunger critical but was "
                    + hungry.needs().hunger() + " (lifecycle=" + hungry.lifecycle() + ", task="
                    + hungry.activeTask().map(t -> t.kind() + "/" + t.state()).orElse("none")
                    + ", storageBread=" + breadCount(storage) + ")");
            helper.assertValueEqual(breadCount(storage), 3, "unlinked storage is never drawn from");
            helper.succeed();
        });
    }

    private static int breadCount(VillageStorageBlockEntity storage) {
        return storage.snapshotCounts().getOrDefault(BREAD, 0);
    }
}
