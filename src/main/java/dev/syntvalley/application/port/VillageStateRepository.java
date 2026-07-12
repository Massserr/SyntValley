package dev.syntvalley.application.port;

import dev.syntvalley.domain.identity.VillageId;
import dev.syntvalley.domain.village.VillageAggregate;
import java.util.Optional;

public interface VillageStateRepository {
    boolean isAvailable();

    Optional<VillageAggregate> find(VillageId villageId);

    int villageCount();

    RepositoryCommitResult create(VillageAggregate village);

    RepositoryCommitResult update(VillageAggregate village, long expectedRevision);
}
