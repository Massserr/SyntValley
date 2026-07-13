package dev.syntvalley.content.blockentity;

import dev.syntvalley.application.service.CitizenApplicationService;
import dev.syntvalley.application.service.CoreBindingService;
import dev.syntvalley.bootstrap.ServerRuntimeManager;
import dev.syntvalley.bootstrap.SyntValleyServerRuntime;
import dev.syntvalley.content.entity.SyntCitizenEntity;
import dev.syntvalley.domain.village.CoreBinding;
import dev.syntvalley.domain.village.CoreLocation;
import dev.syntvalley.domain.village.VillageAggregate;
import dev.syntvalley.observability.SyntValleyLog;
import dev.syntvalley.persistence.codec.CoreBindingNbtCodec;
import dev.syntvalley.registry.ModBlockEntities;
import dev.syntvalley.registry.ModEntityTypes;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Local binding hint only. Canonical Village data is never stored in this BlockEntity. */
public final class SyntCoreBlockEntity extends BlockEntity {
    private static final int HIRE_EMERALD_COST = 5;

    private CoreBinding binding;
    private boolean invalidBinding;
    private boolean diagnosticLogged;

    public SyntCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SYNT_CORE.get(), pos, state);
    }

    public Optional<CoreBinding> binding() {
        return Optional.ofNullable(binding);
    }

    public boolean hasInvalidBinding() {
        return invalidBinding;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            ensureServerBinding(serverLevel);
        }
    }

    public void ensureServerBinding(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        if (invalidBinding) {
            logDiagnosticOnce("Core has quarantined local binding NBT");
            return;
        }

        SyntValleyServerRuntime runtime = ServerRuntimeManager.getOrCreate(level.getServer());
        CoreBindingService.EnsureBindingResult result = runtime.ensureCoreBound(
                binding(),
                coreLocation(level),
                level.getGameTime()
        );
        if (result instanceof CoreBindingService.EnsureBindingResult.Bound bound) {
            applyBinding(bound.binding());
            if (bound.outcome() == CoreBindingService.BindingOutcome.CREATED) {
                SyntValleyLog.logger().info(
                        "Created Village {} for Synt Core at {} {}",
                        bound.village().id(),
                        level.dimension().location(),
                        worldPosition
                );
            } else if (bound.outcome() == CoreBindingService.BindingOutcome.REBOUND) {
                SyntValleyLog.logger().info(
                        "Rebound Village {} to Synt Core generation {} at {} {}",
                        bound.village().id(),
                        bound.binding().generation(),
                        level.dimension().location(),
                        worldPosition
                );
            }
            return;
        }

        CoreBindingService.EnsureBindingResult.Rejected rejected =
                (CoreBindingService.EnsureBindingResult.Rejected) result;
        logDiagnosticOnce("Core binding rejected: " + rejected.reason());
    }

    public void handleServerRemoval(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        if (invalidBinding || binding == null) {
            return;
        }

        SyntValleyServerRuntime runtime = ServerRuntimeManager.getOrCreate(level.getServer());
        CoreBindingService.RemoveBindingResult result = runtime.removeCore(binding, coreLocation(level));
        if (result instanceof CoreBindingService.RemoveBindingResult.Orphaned orphaned) {
            if (orphaned.changed()) {
                SyntValleyLog.logger().info(
                        "Village {} became ORPHANED after Synt Core removal at {} {}",
                        orphaned.village().id(),
                        level.dimension().location(),
                        worldPosition
                );
            }
            return;
        }

        CoreBindingService.RemoveBindingResult.Rejected rejected =
                (CoreBindingService.RemoveBindingResult.Rejected) result;
        logDiagnosticOnce("Core removal reconciliation rejected: " + rejected.reason());
    }

    public Optional<InspectView> inspect(ServerLevel level) {
        if (invalidBinding || binding == null) {
            return Optional.empty();
        }
        return ServerRuntimeManager.getOrCreate(level.getServer())
                .inspectVillage(binding.villageId())
                .map(InspectView::fromVillage);
    }

    /** Selects this Core's Village as the player's pending Village Console link target. */
    public void selectForLink(ServerLevel level, Player player) {
        if (invalidBinding || binding == null) {
            return;
        }
        ServerRuntimeManager.getOrCreate(level.getServer())
                .selectVillageForLink(player.getUUID(), binding.villageId(), level.getGameTime());
    }

    /**
     * Server-authoritative hire triggered by a player. Validates binding, payment and spawn space,
     * creates the canonical Citizen record via the runtime, then spawns its bound entity near the Core.
     */
    public HireResultStatus hireCitizen(ServerLevel level, Player player, ItemStack payment) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(payment, "payment");

        ensureServerBinding(level);
        if (invalidBinding || binding == null) {
            return HireResultStatus.CORE_UNAVAILABLE;
        }

        boolean creative = player.getAbilities().instabuild;
        if (!creative && payment.getCount() < HIRE_EMERALD_COST) {
            return HireResultStatus.INSUFFICIENT_PAYMENT;
        }

        Optional<BlockPos> spawnPos = findSpawnPos(level);
        if (spawnPos.isEmpty()) {
            return HireResultStatus.NO_SPAWN_SPACE;
        }

        // Instantiate the entity before creating the canonical record so a spawn failure never
        // leaves an orphaned Citizen record behind.
        SyntCitizenEntity citizen = ModEntityTypes.SYNT_CITIZEN.get().create(level);
        if (citizen == null) {
            logDiagnosticOnce("Citizen entity type failed to instantiate");
            return HireResultStatus.REJECTED;
        }

        SyntValleyServerRuntime runtime = ServerRuntimeManager.getOrCreate(level.getServer());
        CitizenApplicationService.HireResult result = runtime.hireCitizen(binding.villageId(), null, level.getGameTime());
        if (!(result instanceof CitizenApplicationService.HireResult.Hired hired)) {
            CitizenApplicationService.HireRejection reason =
                    ((CitizenApplicationService.HireResult.Rejected) result).reason();
            logDiagnosticOnce("Citizen hire rejected: " + reason);
            return reason == CitizenApplicationService.HireRejection.VILLAGE_FULL
                    ? HireResultStatus.VILLAGE_FULL
                    : HireResultStatus.REJECTED;
        }

        citizen.applyBinding(hired.citizen().entityBinding());
        BlockPos pos = spawnPos.orElseThrow();
        citizen.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        level.addFreshEntity(citizen);

        if (!creative) {
            payment.shrink(HIRE_EMERALD_COST);
        }
        return HireResultStatus.HIRED;
    }

    private Optional<BlockPos> findSpawnPos(ServerLevel level) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos adjacent = worldPosition.relative(direction);
            for (int dy = 0; dy >= -1; dy--) {
                BlockPos feet = adjacent.above(dy);
                if (isStandable(level, feet)) {
                    return Optional.of(feet);
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isStandable(ServerLevel level, BlockPos feet) {
        return !level.getBlockState(feet.below()).isAir()
                && level.getBlockState(feet).isAir()
                && level.getBlockState(feet.above()).isAir();
    }

    public enum HireResultStatus {
        HIRED("message.syntvalley.core.hire.success"),
        INSUFFICIENT_PAYMENT("message.syntvalley.core.hire.payment"),
        NO_SPAWN_SPACE("message.syntvalley.core.hire.space"),
        VILLAGE_FULL("message.syntvalley.core.hire.full"),
        CORE_UNAVAILABLE("message.syntvalley.core.unavailable"),
        REJECTED("message.syntvalley.core.hire.rejected");

        private final String messageKey;

        HireResultStatus(String messageKey) {
            this.messageKey = messageKey;
        }

        public String messageKey() {
            return messageKey;
        }
    }

    private void applyBinding(CoreBinding replacement) {
        Objects.requireNonNull(replacement, "replacement");
        if (replacement.equals(binding) && !invalidBinding) {
            return;
        }
        binding = replacement;
        invalidBinding = false;
        diagnosticLogged = false;
        setChanged();
    }

    private CoreLocation coreLocation(ServerLevel level) {
        return new CoreLocation(level.dimension().location().toString(), worldPosition.asLong());
    }

    private void logDiagnosticOnce(String message) {
        if (diagnosticLogged) {
            return;
        }
        diagnosticLogged = true;
        SyntValleyLog.logger().error("{} at {}", message, worldPosition);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        binding = null;
        diagnosticLogged = false;
        CoreBindingNbtCodec.LocalBindingState decoded = CoreBindingNbtCodec.decode(tag);
        if (decoded instanceof CoreBindingNbtCodec.LocalBindingState.Bound bound) {
            binding = bound.binding();
            invalidBinding = false;
        } else {
            invalidBinding = decoded instanceof CoreBindingNbtCodec.LocalBindingState.Invalid;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        CoreBindingNbtCodec.LocalBindingState state = invalidBinding
                ? new CoreBindingNbtCodec.LocalBindingState.Invalid()
                : binding == null
                        ? new CoreBindingNbtCodec.LocalBindingState.Unbound()
                        : new CoreBindingNbtCodec.LocalBindingState.Bound(binding);
        CoreBindingNbtCodec.encode(tag, state);
    }

    public record InspectView(String villageId, String lifecycle, long revision) {
        private static InspectView fromVillage(VillageAggregate village) {
            return new InspectView(village.id().toString(), village.lifecycle().name(), village.revision());
        }
    }
}
