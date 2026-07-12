package dev.syntvalley.application.service;

import dev.syntvalley.application.port.CitizenCommitResult;
import dev.syntvalley.application.port.CitizenStateRepository;
import dev.syntvalley.application.port.RepositoryRejection;
import dev.syntvalley.domain.citizen.CitizenAggregate;
import dev.syntvalley.domain.citizen.CitizenEntityBinding;
import dev.syntvalley.domain.citizen.CitizenTransitionRejection;
import dev.syntvalley.domain.citizen.CitizenTransitionResult;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** The only application service allowed to change the Entity ↔ Citizen presence relationship. */
public final class CitizenBindingService {
    private final CitizenStateRepository repository;

    public CitizenBindingService(CitizenStateRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /**
     * Reconciles a loaded or freshly spawned entity against its canonical Citizen record. A missing
     * record or a conflicting/duplicate entity is rejected so the caller can quarantine the entity
     * rather than silently create a new identity.
     */
    public EnsureCitizenResult ensureBound(CitizenEntityBinding binding, UUID entityId) {
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(entityId, "entityId");

        if (!repository.isAvailable()) {
            return new EnsureCitizenResult.Rejected(CitizenBindingRejection.PERSISTENCE_UNAVAILABLE);
        }

        Optional<CitizenAggregate> current = repository.find(binding.citizenId());
        if (current.isEmpty()) {
            return new EnsureCitizenResult.Rejected(CitizenBindingRejection.MISSING_CITIZEN);
        }

        CitizenAggregate citizen = current.orElseThrow();
        CitizenTransitionResult transition = citizen.reconcileEntity(binding, entityId);
        if (transition instanceof CitizenTransitionResult.Unchanged unchanged) {
            return new EnsureCitizenResult.Bound(unchanged.citizen(), PresenceOutcome.EXISTING);
        }
        if (transition instanceof CitizenTransitionResult.Rejected rejected) {
            return new EnsureCitizenResult.Rejected(mapTransitionRejection(rejected.reason()));
        }

        CitizenAggregate claimed = ((CitizenTransitionResult.Changed) transition).citizen();
        CitizenCommitResult committed = repository.update(claimed, citizen.revision());
        if (committed instanceof CitizenCommitResult.Committed success) {
            return new EnsureCitizenResult.Bound(success.citizen(), PresenceOutcome.CLAIMED);
        }
        CitizenCommitResult.Rejected rejected = (CitizenCommitResult.Rejected) committed;
        return new EnsureCitizenResult.Rejected(mapRepositoryRejection(rejected.reason()));
    }

    /**
     * Records the death of the bound entity. Death is a terminal transition to DECEASED; it never
     * respawns or deletes the record.
     */
    public DeathResult recordDeath(CitizenEntityBinding binding, UUID entityId) {
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(entityId, "entityId");

        if (!repository.isAvailable()) {
            return new DeathResult.Rejected(CitizenBindingRejection.PERSISTENCE_UNAVAILABLE);
        }

        Optional<CitizenAggregate> current = repository.find(binding.citizenId());
        if (current.isEmpty()) {
            return new DeathResult.Rejected(CitizenBindingRejection.MISSING_CITIZEN);
        }

        CitizenAggregate citizen = current.orElseThrow();
        CitizenTransitionResult transition = citizen.recordDeath(entityId);
        if (transition instanceof CitizenTransitionResult.Unchanged unchanged) {
            return new DeathResult.Recorded(unchanged.citizen(), false);
        }
        if (transition instanceof CitizenTransitionResult.Rejected rejected) {
            return new DeathResult.Rejected(mapTransitionRejection(rejected.reason()));
        }

        CitizenAggregate deceased = ((CitizenTransitionResult.Changed) transition).citizen();
        CitizenCommitResult committed = repository.update(deceased, citizen.revision());
        if (committed instanceof CitizenCommitResult.Committed success) {
            return new DeathResult.Recorded(success.citizen(), true);
        }
        CitizenCommitResult.Rejected rejected = (CitizenCommitResult.Rejected) committed;
        return new DeathResult.Rejected(mapRepositoryRejection(rejected.reason()));
    }

    private static CitizenBindingRejection mapTransitionRejection(CitizenTransitionRejection reason) {
        return switch (reason) {
            case BINDING_CONFLICT -> CitizenBindingRejection.BINDING_CONFLICT;
            case STALE_BINDING -> CitizenBindingRejection.STALE_BINDING;
            case LIFECYCLE_DISALLOWS_PRESENCE -> CitizenBindingRejection.LIFECYCLE_DISALLOWS_PRESENCE;
            case DUPLICATE_ENTITY -> CitizenBindingRejection.DUPLICATE_ENTITY;
            case REVISION_EXHAUSTED -> CitizenBindingRejection.REVISION_EXHAUSTED;
        };
    }

    private static CitizenBindingRejection mapRepositoryRejection(RepositoryRejection reason) {
        return switch (reason) {
            case MISSING_RECORD -> CitizenBindingRejection.MISSING_CITIZEN;
            case REVISION_CONFLICT -> CitizenBindingRejection.REVISION_CONFLICT;
            case DATA_REVISION_EXHAUSTED -> CitizenBindingRejection.REVISION_EXHAUSTED;
            case PERSISTENCE_UNAVAILABLE -> CitizenBindingRejection.PERSISTENCE_UNAVAILABLE;
            case DUPLICATE_ID, CAPACITY_REACHED -> CitizenBindingRejection.BINDING_CONFLICT;
        };
    }

    public enum PresenceOutcome {
        CLAIMED,
        EXISTING
    }

    public enum CitizenBindingRejection {
        MISSING_CITIZEN,
        BINDING_CONFLICT,
        STALE_BINDING,
        LIFECYCLE_DISALLOWS_PRESENCE,
        DUPLICATE_ENTITY,
        REVISION_CONFLICT,
        REVISION_EXHAUSTED,
        PERSISTENCE_UNAVAILABLE,
        RUNTIME_STOPPING
    }

    public sealed interface EnsureCitizenResult {
        record Bound(CitizenAggregate citizen, PresenceOutcome outcome) implements EnsureCitizenResult {
            public Bound {
                Objects.requireNonNull(citizen, "citizen");
                Objects.requireNonNull(outcome, "outcome");
            }

            public CitizenEntityBinding binding() {
                return citizen.entityBinding();
            }
        }

        record Rejected(CitizenBindingRejection reason) implements EnsureCitizenResult {
            public Rejected {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }

    public sealed interface DeathResult {
        record Recorded(CitizenAggregate citizen, boolean changed) implements DeathResult {
            public Recorded {
                Objects.requireNonNull(citizen, "citizen");
            }
        }

        record Rejected(CitizenBindingRejection reason) implements DeathResult {
            public Rejected {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }
}
