package dev.syntvalley.application.port;

import dev.syntvalley.domain.citizen.CitizenAggregate;
import dev.syntvalley.domain.identity.CitizenId;
import dev.syntvalley.domain.identity.VillageId;
import java.util.List;
import java.util.Optional;

public interface CitizenStateRepository {
    boolean isAvailable();

    Optional<CitizenAggregate> find(CitizenId citizenId);

    /** Count of simultaneously ACTIVE citizens bound to the given Village, for the hire cap. */
    int activeCountForVillage(VillageId villageId);

    /** Count of all citizens (any lifecycle) bound to the given Village, for read models. */
    int countForVillage(VillageId villageId);

    /** Up to {@code limit} citizens (any lifecycle) bound to the given Village, for read models. */
    List<CitizenAggregate> findByVillage(VillageId villageId, int limit);

    int citizenCount();

    CitizenCommitResult create(CitizenAggregate citizen);

    CitizenCommitResult update(CitizenAggregate citizen, long expectedRevision);
}
