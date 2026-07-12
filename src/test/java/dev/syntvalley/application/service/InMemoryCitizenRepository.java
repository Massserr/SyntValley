package dev.syntvalley.application.service;

import dev.syntvalley.application.port.CitizenCommitResult;
import dev.syntvalley.application.port.CitizenStateRepository;
import dev.syntvalley.application.port.RepositoryRejection;
import dev.syntvalley.domain.citizen.CitizenAggregate;
import dev.syntvalley.domain.citizen.CitizenLifecycle;
import dev.syntvalley.domain.identity.CitizenId;
import dev.syntvalley.domain.identity.VillageId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Deterministic in-memory CitizenStateRepository for pure-Java application tests. */
final class InMemoryCitizenRepository implements CitizenStateRepository {
    private final Map<CitizenId, CitizenAggregate> citizens = new LinkedHashMap<>();

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Optional<CitizenAggregate> find(CitizenId citizenId) {
        return Optional.ofNullable(citizens.get(citizenId));
    }

    @Override
    public int activeCountForVillage(VillageId villageId) {
        return (int) citizens.values().stream()
                .filter(citizen -> citizen.lifecycle() == CitizenLifecycle.ACTIVE)
                .filter(citizen -> citizen.villageId().equals(villageId))
                .count();
    }

    @Override
    public int citizenCount() {
        return citizens.size();
    }

    @Override
    public CitizenCommitResult create(CitizenAggregate citizen) {
        if (citizens.putIfAbsent(citizen.id(), citizen) != null) {
            return new CitizenCommitResult.Rejected(RepositoryRejection.DUPLICATE_ID);
        }
        return new CitizenCommitResult.Committed(citizen);
    }

    @Override
    public CitizenCommitResult update(CitizenAggregate citizen, long expectedRevision) {
        CitizenAggregate current = citizens.get(citizen.id());
        if (current == null) {
            return new CitizenCommitResult.Rejected(RepositoryRejection.MISSING_RECORD);
        }
        if (current.revision() != expectedRevision || citizen.revision() != expectedRevision + 1) {
            return new CitizenCommitResult.Rejected(RepositoryRejection.REVISION_CONFLICT, Optional.of(current));
        }
        citizens.put(citizen.id(), citizen);
        return new CitizenCommitResult.Committed(citizen);
    }
}
