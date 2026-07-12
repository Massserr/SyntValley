package dev.syntvalley.application.service;

import dev.syntvalley.application.port.RepositoryCommitResult;
import dev.syntvalley.application.port.RepositoryRejection;
import dev.syntvalley.application.port.VillageStateRepository;
import dev.syntvalley.domain.identity.VillageId;
import dev.syntvalley.domain.village.CoreBinding;
import dev.syntvalley.domain.village.CoreLocation;
import dev.syntvalley.domain.village.VillageAggregate;
import dev.syntvalley.domain.village.VillageTransitionRejection;
import dev.syntvalley.domain.village.VillageTransitionResult;
import java.util.Objects;
import java.util.Optional;

/** The only application service allowed to change the Core ↔ Village relationship. */
public final class CoreBindingService {
    private final VillageStateRepository repository;
    private final VillageApplicationService villageService;

    public CoreBindingService(VillageStateRepository repository, VillageApplicationService villageService) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.villageService = Objects.requireNonNull(villageService, "villageService");
    }

    public EnsureBindingResult ensureBound(Optional<CoreBinding> localBinding, CoreLocation observedLocation, long gameTime) {
        Objects.requireNonNull(localBinding, "localBinding");
        Objects.requireNonNull(observedLocation, "observedLocation");

        if (!repository.isAvailable()) {
            return new EnsureBindingResult.Rejected(CoreBindingRejection.PERSISTENCE_UNAVAILABLE);
        }

        if (localBinding.isEmpty()) {
            VillageApplicationService.CreateVillageResult created = villageService.createAt(observedLocation, gameTime);
            if (created instanceof VillageApplicationService.CreateVillageResult.Created success) {
                return new EnsureBindingResult.Bound(success.village(), BindingOutcome.CREATED);
            }
            VillageApplicationService.CreateVillageResult.Rejected rejected =
                    (VillageApplicationService.CreateVillageResult.Rejected) created;
            return new EnsureBindingResult.Rejected(mapRepositoryRejection(rejected.reason()));
        }

        CoreBinding binding = localBinding.orElseThrow();
        Optional<VillageAggregate> current = repository.find(binding.villageId());
        if (current.isEmpty()) {
            return new EnsureBindingResult.Rejected(CoreBindingRejection.MISSING_VILLAGE);
        }

        VillageAggregate village = current.orElseThrow();
        VillageTransitionResult transition = village.reconcileCore(binding, observedLocation);
        if (transition instanceof VillageTransitionResult.Unchanged unchanged) {
            return new EnsureBindingResult.Bound(unchanged.village(), BindingOutcome.EXISTING);
        }
        if (transition instanceof VillageTransitionResult.Rejected rejected) {
            return new EnsureBindingResult.Rejected(mapTransitionRejection(rejected.reason()));
        }

        VillageAggregate rebound = ((VillageTransitionResult.Changed) transition).village();
        RepositoryCommitResult committed = repository.update(rebound, village.revision());
        if (committed instanceof RepositoryCommitResult.Committed success) {
            return new EnsureBindingResult.Bound(success.village(), BindingOutcome.REBOUND);
        }
        RepositoryCommitResult.Rejected rejected = (RepositoryCommitResult.Rejected) committed;
        return new EnsureBindingResult.Rejected(mapRepositoryRejection(rejected.reason()));
    }

    public RemoveBindingResult remove(CoreBinding localBinding, CoreLocation removedLocation) {
        Objects.requireNonNull(localBinding, "localBinding");
        Objects.requireNonNull(removedLocation, "removedLocation");

        if (!repository.isAvailable()) {
            return new RemoveBindingResult.Rejected(CoreBindingRejection.PERSISTENCE_UNAVAILABLE);
        }

        Optional<VillageAggregate> current = repository.find(localBinding.villageId());
        if (current.isEmpty()) {
            return new RemoveBindingResult.Rejected(CoreBindingRejection.MISSING_VILLAGE);
        }

        VillageAggregate village = current.orElseThrow();
        VillageTransitionResult transition = village.orphanCore(localBinding, removedLocation);
        if (transition instanceof VillageTransitionResult.Unchanged unchanged) {
            return new RemoveBindingResult.Orphaned(unchanged.village(), false);
        }
        if (transition instanceof VillageTransitionResult.Rejected rejected) {
            return new RemoveBindingResult.Rejected(mapTransitionRejection(rejected.reason()));
        }

        VillageAggregate orphaned = ((VillageTransitionResult.Changed) transition).village();
        RepositoryCommitResult committed = repository.update(orphaned, village.revision());
        if (committed instanceof RepositoryCommitResult.Committed success) {
            return new RemoveBindingResult.Orphaned(success.village(), true);
        }
        RepositoryCommitResult.Rejected rejected = (RepositoryCommitResult.Rejected) committed;
        return new RemoveBindingResult.Rejected(mapRepositoryRejection(rejected.reason()));
    }

    public Optional<VillageAggregate> inspect(VillageId villageId) {
        return repository.find(Objects.requireNonNull(villageId, "villageId"));
    }

    private static CoreBindingRejection mapTransitionRejection(VillageTransitionRejection reason) {
        return switch (reason) {
            case BINDING_CONFLICT -> CoreBindingRejection.BINDING_CONFLICT;
            case STALE_BINDING -> CoreBindingRejection.STALE_BINDING;
            case LIFECYCLE_DISALLOWS_BINDING -> CoreBindingRejection.LIFECYCLE_DISALLOWS_BINDING;
            case GENERATION_EXHAUSTED -> CoreBindingRejection.GENERATION_EXHAUSTED;
            case REVISION_EXHAUSTED -> CoreBindingRejection.REVISION_EXHAUSTED;
        };
    }

    private static CoreBindingRejection mapRepositoryRejection(RepositoryRejection reason) {
        return switch (reason) {
            case DUPLICATE_ID -> CoreBindingRejection.ID_COLLISION;
            case MISSING_RECORD -> CoreBindingRejection.MISSING_VILLAGE;
            case REVISION_CONFLICT -> CoreBindingRejection.REVISION_CONFLICT;
            case CAPACITY_REACHED -> CoreBindingRejection.CAPACITY_REACHED;
            case DATA_REVISION_EXHAUSTED -> CoreBindingRejection.REVISION_EXHAUSTED;
            case PERSISTENCE_UNAVAILABLE -> CoreBindingRejection.PERSISTENCE_UNAVAILABLE;
        };
    }

    public enum BindingOutcome {
        CREATED,
        EXISTING,
        REBOUND
    }

    public enum CoreBindingRejection {
        MISSING_VILLAGE,
        BINDING_CONFLICT,
        STALE_BINDING,
        LIFECYCLE_DISALLOWS_BINDING,
        GENERATION_EXHAUSTED,
        REVISION_EXHAUSTED,
        REVISION_CONFLICT,
        CAPACITY_REACHED,
        ID_COLLISION,
        PERSISTENCE_UNAVAILABLE,
        RUNTIME_STOPPING
    }

    public sealed interface EnsureBindingResult {
        record Bound(VillageAggregate village, BindingOutcome outcome) implements EnsureBindingResult {
            public Bound {
                Objects.requireNonNull(village, "village");
                Objects.requireNonNull(outcome, "outcome");
            }

            public CoreBinding binding() {
                return village.coreBinding();
            }
        }

        record Rejected(CoreBindingRejection reason) implements EnsureBindingResult {
            public Rejected {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }

    public sealed interface RemoveBindingResult {
        record Orphaned(VillageAggregate village, boolean changed) implements RemoveBindingResult {
            public Orphaned {
                Objects.requireNonNull(village, "village");
            }
        }

        record Rejected(CoreBindingRejection reason) implements RemoveBindingResult {
            public Rejected {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }
}
