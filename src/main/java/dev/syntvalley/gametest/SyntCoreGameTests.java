package dev.syntvalley.gametest;

import dev.syntvalley.bootstrap.ProjectIdentity;
import dev.syntvalley.bootstrap.ServerRuntimeManager;
import dev.syntvalley.content.blockentity.SyntCoreBlockEntity;
import dev.syntvalley.domain.village.CoreBinding;
import dev.syntvalley.domain.village.VillageAggregate;
import dev.syntvalley.domain.village.VillageLifecycle;
import dev.syntvalley.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ProjectIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SyntCoreGameTests {
    private static final BlockPos CORE_POS = BlockPos.ZERO;

    private SyntCoreGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void placementCreatesPersistentBinding(GameTestHelper helper) {
        helper.setBlock(CORE_POS, ModBlocks.SYNT_CORE.get());
        helper.runAfterDelay(2, () -> {
            SyntCoreBlockEntity core = helper.getBlockEntity(CORE_POS);
            CoreBinding binding = core.binding().orElseThrow();
            VillageAggregate village = ServerRuntimeManager.getOrCreate(helper.getLevel().getServer())
                    .inspectVillage(binding.villageId())
                    .orElseThrow();

            helper.assertValueEqual(village.lifecycle(), VillageLifecycle.ACTIVE, "Village lifecycle");
            helper.assertValueEqual(village.coreBinding(), binding, "Core binding");
            helper.assertValueEqual(
                    village.coreLocation().orElseThrow().packedPos(),
                    helper.absolutePos(CORE_POS).asLong(),
                    "Core position"
            );
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void repeatedBindingCallbackIsIdempotent(GameTestHelper helper) {
        helper.setBlock(CORE_POS, ModBlocks.SYNT_CORE.get());
        helper.runAfterDelay(2, () -> {
            SyntCoreBlockEntity core = helper.getBlockEntity(CORE_POS);
            CoreBinding original = core.binding().orElseThrow();
            core.ensureServerBinding(helper.getLevel());
            core.ensureServerBinding(helper.getLevel());

            CoreBinding replayed = core.binding().orElseThrow();
            VillageAggregate village = ServerRuntimeManager.getOrCreate(helper.getLevel().getServer())
                    .inspectVillage(original.villageId())
                    .orElseThrow();
            helper.assertValueEqual(replayed, original, "replayed binding");
            helper.assertValueEqual(village.revision(), 1L, "Village revision after replay");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void breakingCoreOrphansVillage(GameTestHelper helper) {
        helper.setBlock(CORE_POS, ModBlocks.SYNT_CORE.get());
        helper.runAfterDelay(2, () -> {
            SyntCoreBlockEntity core = helper.getBlockEntity(CORE_POS);
            CoreBinding binding = core.binding().orElseThrow();
            helper.destroyBlock(CORE_POS);
            helper.runAfterDelay(2, () -> {
                VillageAggregate village = ServerRuntimeManager.getOrCreate(helper.getLevel().getServer())
                        .inspectVillage(binding.villageId())
                        .orElseThrow();
                helper.assertValueEqual(village.lifecycle(), VillageLifecycle.ORPHANED, "orphan lifecycle");
                helper.assertTrue(village.coreLocation().isEmpty(), "Orphaned Village retained active Core location");
                helper.assertValueEqual(village.revision(), 2L, "Village revision after Core break");
                helper.succeed();
            });
        });
    }
}
