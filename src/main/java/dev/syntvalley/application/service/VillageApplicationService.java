package dev.syntvalley.application.service;

import dev.syntvalley.application.port.RepositoryCommitResult;
import dev.syntvalley.application.port.RepositoryRejection;
import dev.syntvalley.application.port.VillageStateRepository;
import dev.syntvalley.domain.identity.VillageId;
import dev.syntvalley.domain.village.CoreLocation;
import dev.syntvalley.domain.village.VillageAggregate;
import java.util.Objects;
import java.util.function.Supplier;

public final class VillageApplicationService {
    public static final String DEFAULT_VILLAGE_NAME = "New Synt Village";
    private static final int MAX_ID_GENERATION_ATTEMPTS = 3;

    private final VillageStateRepository repository;
    private final Supplier<VillageId> idFactory;

    public VillageApplicationService(VillageStateRepository repository, Supplier<VillageId> idFactory) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.idFactory = Objects.requireNonNull(idFactory, "idFactory");
    }

    public CreateVillageResult createAt(CoreLocation coreLocation, long gameTime) {
        Objects.requireNonNull(coreLocation, "coreLocation");

        for (int attempt = 0; attempt < MAX_ID_GENERATION_ATTEMPTS; attempt++) {
            VillageAggregate candidate = VillageAggregate.create(
                    Objects.requireNonNull(idFactory.get(), "idFactory result"),
                    DEFAULT_VILLAGE_NAME,
                    coreLocation,
                    gameTime
            );
            RepositoryCommitResult result = repository.create(candidate);
            if (result instanceof RepositoryCommitResult.Committed committed) {
                return new CreateVillageResult.Created(committed.village());
            }
            if (result instanceof RepositoryCommitResult.Rejected rejected
                    && rejected.reason() != RepositoryRejection.DUPLICATE_ID) {
                return new CreateVillageResult.Rejected(rejected.reason());
            }
        }

        return new CreateVillageResult.Rejected(RepositoryRejection.DUPLICATE_ID);
    }

    public sealed interface CreateVillageResult {
        record Created(VillageAggregate village) implements CreateVillageResult {
            public Created {
                Objects.requireNonNull(village, "village");
            }
        }

        record Rejected(RepositoryRejection reason) implements CreateVillageResult {
            public Rejected {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }
}
