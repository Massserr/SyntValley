package dev.syntvalley.content.entity;

import dev.syntvalley.application.service.CitizenBindingService;
import dev.syntvalley.bootstrap.ServerRuntimeManager;
import dev.syntvalley.domain.citizen.CitizenEntityBinding;
import dev.syntvalley.observability.SyntValleyLog;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Server-authoritative living presence of one canonical Citizen. The entity owns no gameplay truth:
 * it carries only its binding and delegates identity reconciliation and death to the world runtime.
 */
public final class SyntCitizenEntity extends PathfinderMob {
    private CitizenEntityBinding binding;
    private boolean invalidBinding;
    private boolean reconciled;
    private boolean diagnosticLogged;

    public SyntCitizenEntity(EntityType<? extends SyntCitizenEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.5));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    /** Attaches the binding for a freshly hired citizen before it is added to the world. */
    public void applyBinding(CitizenEntityBinding binding) {
        this.binding = Objects.requireNonNull(binding, "binding");
        this.invalidBinding = false;
        this.reconciled = false;
        this.diagnosticLogged = false;
    }

    public Optional<CitizenEntityBinding> binding() {
        return Optional.ofNullable(binding);
    }

    public boolean hasInvalidBinding() {
        return invalidBinding;
    }

    @Override
    public void tick() {
        super.tick();
        if (!reconciled && level() instanceof ServerLevel serverLevel) {
            reconciled = true;
            reconcile(serverLevel);
        }
    }

    private void reconcile(ServerLevel level) {
        if (invalidBinding) {
            quarantine("Synt Citizen has quarantined local binding NBT");
            return;
        }
        if (binding == null) {
            quarantine("Synt Citizen loaded without a binding");
            return;
        }

        CitizenBindingService.EnsureCitizenResult result =
                ServerRuntimeManager.getOrCreate(level.getServer()).ensureCitizenBound(binding, getUUID());
        if (result instanceof CitizenBindingService.EnsureCitizenResult.Rejected rejected) {
            if (isIdentityConflict(rejected.reason())) {
                quarantine("Synt Citizen binding rejected: " + rejected.reason());
            } else {
                logDiagnosticOnce("Synt Citizen binding deferred: " + rejected.reason());
            }
        }
    }

    private static boolean isIdentityConflict(CitizenBindingService.CitizenBindingRejection reason) {
        return switch (reason) {
            case MISSING_CITIZEN, DUPLICATE_ENTITY, BINDING_CONFLICT, STALE_BINDING, LIFECYCLE_DISALLOWS_PRESENCE -> true;
            case REVISION_CONFLICT, REVISION_EXHAUSTED, PERSISTENCE_UNAVAILABLE, RUNTIME_STOPPING -> false;
        };
    }

    private void quarantine(String message) {
        invalidBinding = true;
        logDiagnosticOnce(message);
        discard();
    }

    private void logDiagnosticOnce(String message) {
        if (diagnosticLogged) {
            return;
        }
        diagnosticLogged = true;
        SyntValleyLog.logger().error("{} at {} (uuid {})", message, blockPosition(), getUUID());
    }

    @Override
    public void die(DamageSource cause) {
        if (!invalidBinding && binding != null && level() instanceof ServerLevel serverLevel) {
            ServerRuntimeManager.getOrCreate(serverLevel.getServer()).recordCitizenDeath(binding, getUUID());
        }
        super.die(cause);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        CitizenEntityBindingNbtCodec.LocalBindingState state = invalidBinding
                ? new CitizenEntityBindingNbtCodec.LocalBindingState.Invalid()
                : binding == null
                        ? new CitizenEntityBindingNbtCodec.LocalBindingState.Unbound()
                        : new CitizenEntityBindingNbtCodec.LocalBindingState.Bound(binding);
        CitizenEntityBindingNbtCodec.encode(tag, state);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        binding = null;
        reconciled = false;
        diagnosticLogged = false;
        CitizenEntityBindingNbtCodec.LocalBindingState state = CitizenEntityBindingNbtCodec.decode(tag);
        if (state instanceof CitizenEntityBindingNbtCodec.LocalBindingState.Bound bound) {
            binding = bound.binding();
            invalidBinding = false;
        } else {
            invalidBinding = state instanceof CitizenEntityBindingNbtCodec.LocalBindingState.Invalid;
        }
    }
}
