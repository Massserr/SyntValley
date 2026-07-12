package dev.syntvalley.application.service;

import dev.syntvalley.application.port.CitizenCommitResult;
import dev.syntvalley.application.port.CitizenStateRepository;
import dev.syntvalley.application.port.RepositoryRejection;
import dev.syntvalley.application.port.VillageStateRepository;
import dev.syntvalley.domain.citizen.CitizenAggregate;
import dev.syntvalley.domain.citizen.CitizenConstraints;
import dev.syntvalley.domain.identity.CitizenId;
import dev.syntvalley.domain.identity.VillageId;
import dev.syntvalley.domain.village.VillageAggregate;
import dev.syntvalley.domain.village.VillageLifecycle;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The single server-authoritative entry point that creates Citizens. Manual hire calls it now;
 * later population mechanics (immigration, and eventually breeding) can route through the same
 * validated path without duplicating the domain rules.
 */
public final class CitizenApplicationService {
    public static final String DEFAULT_CITIZEN_NAME = "Settler";
    private static final int MAX_ID_GENERATION_ATTEMPTS = 3;

    private final CitizenStateRepository citizens;
    private final VillageStateRepository villages;
    private final Supplier<CitizenId> idFactory;

    public CitizenApplicationService(
            CitizenStateRepository citizens,
            VillageStateRepository villages,
            Supplier<CitizenId> idFactory
    ) {
        this.citizens = Objects.requireNonNull(citizens, "citizens");
        this.villages = Objects.requireNonNull(villages, "villages");
        this.idFactory = Objects.requireNonNull(idFactory, "idFactory");
    }

    public HireResult hire(VillageId villageId, String requestedName, long gameTime) {
        Objects.requireNonNull(villageId, "villageId");

        if (!citizens.isAvailable() || !villages.isAvailable()) {
            return new HireResult.Rejected(HireRejection.PERSISTENCE_UNAVAILABLE);
        }

        Optional<VillageAggregate> village = villages.find(villageId);
        if (village.isEmpty()) {
            return new HireResult.Rejected(HireRejection.MISSING_VILLAGE);
        }
        if (village.orElseThrow().lifecycle() != VillageLifecycle.ACTIVE) {
            return new HireResult.Rejected(HireRejection.VILLAGE_NOT_ACTIVE);
        }
        if (citizens.activeCountForVillage(villageId) >= CitizenConstraints.MAX_CITIZENS_PER_VILLAGE) {
            return new HireResult.Rejected(HireRejection.VILLAGE_FULL);
        }

        String name = requestedName == null || requestedName.isBlank() ? DEFAULT_CITIZEN_NAME : requestedName;
        if (!CitizenConstraints.isValidName(name)) {
            return new HireResult.Rejected(HireRejection.INVALID_NAME);
        }

        for (int attempt = 0; attempt < MAX_ID_GENERATION_ATTEMPTS; attempt++) {
            CitizenAggregate candidate = CitizenAggregate.create(
                    Objects.requireNonNull(idFactory.get(), "idFactory result"),
                    villageId,
                    name,
                    gameTime
            );
            CitizenCommitResult result = citizens.create(candidate);
            if (result instanceof CitizenCommitResult.Committed committed) {
                return new HireResult.Hired(committed.citizen());
            }
            CitizenCommitResult.Rejected rejected = (CitizenCommitResult.Rejected) result;
            if (rejected.reason() != RepositoryRejection.DUPLICATE_ID) {
                return new HireResult.Rejected(mapRepositoryRejection(rejected.reason()));
            }
        }

        return new HireResult.Rejected(HireRejection.ID_COLLISION);
    }

    public Optional<CitizenAggregate> inspect(CitizenId citizenId) {
        return citizens.find(Objects.requireNonNull(citizenId, "citizenId"));
    }

    private static HireRejection mapRepositoryRejection(RepositoryRejection reason) {
        return switch (reason) {
            case DUPLICATE_ID -> HireRejection.ID_COLLISION;
            case CAPACITY_REACHED -> HireRejection.CAPACITY_REACHED;
            case PERSISTENCE_UNAVAILABLE -> HireRejection.PERSISTENCE_UNAVAILABLE;
            case MISSING_RECORD, REVISION_CONFLICT, DATA_REVISION_EXHAUSTED -> HireRejection.COMMIT_CONFLICT;
        };
    }

    public enum HireRejection {
        MISSING_VILLAGE,
        VILLAGE_NOT_ACTIVE,
        VILLAGE_FULL,
        INVALID_NAME,
        ID_COLLISION,
        CAPACITY_REACHED,
        COMMIT_CONFLICT,
        PERSISTENCE_UNAVAILABLE,
        RUNTIME_STOPPING
    }

    public sealed interface HireResult {
        record Hired(CitizenAggregate citizen) implements HireResult {
            public Hired {
                Objects.requireNonNull(citizen, "citizen");
            }
        }

        record Rejected(HireRejection reason) implements HireResult {
            public Rejected {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }
}
