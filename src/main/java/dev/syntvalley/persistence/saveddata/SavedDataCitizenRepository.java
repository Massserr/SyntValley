package dev.syntvalley.persistence.saveddata;

import dev.syntvalley.application.port.CitizenCommitResult;
import dev.syntvalley.application.port.CitizenStateRepository;
import dev.syntvalley.application.port.RepositoryRejection;
import dev.syntvalley.domain.citizen.CitizenAggregate;
import dev.syntvalley.domain.citizen.CitizenLifecycle;
import dev.syntvalley.domain.identity.CitizenId;
import dev.syntvalley.domain.identity.VillageId;
import dev.syntvalley.persistence.dirty.DirtyKey;
import dev.syntvalley.persistence.dirty.DirtyReason;
import dev.syntvalley.persistence.dirty.DirtyTracker;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;

/** Server-thread adapter from Citizen repository operations to the current SavedData root. */
public final class SavedDataCitizenRepository implements CitizenStateRepository {
    private final MinecraftServer server;
    private final SyntValleySavedData savedData;
    private final DirtyTracker dirtyTracker;

    public SavedDataCitizenRepository(MinecraftServer server, SyntValleySavedData savedData, DirtyTracker dirtyTracker) {
        this.server = Objects.requireNonNull(server, "server");
        this.savedData = Objects.requireNonNull(savedData, "savedData");
        this.dirtyTracker = Objects.requireNonNull(dirtyTracker, "dirtyTracker");
    }

    @Override
    public boolean isAvailable() {
        assertServerThread();
        return savedData.isAvailable();
    }

    @Override
    public Optional<CitizenAggregate> find(CitizenId citizenId) {
        assertServerThread();
        Objects.requireNonNull(citizenId, "citizenId");
        if (!savedData.isAvailable()) {
            return Optional.empty();
        }
        return Optional.ofNullable(savedData.stateSnapshot().citizens().get(citizenId))
                .map(CitizenPersistentRecord::toAggregate);
    }

    @Override
    public int activeCountForVillage(VillageId villageId) {
        assertServerThread();
        Objects.requireNonNull(villageId, "villageId");
        if (!savedData.isAvailable()) {
            return 0;
        }
        int count = 0;
        for (CitizenPersistentRecord record : savedData.stateSnapshot().citizens().values()) {
            if (record.lifecycle() == CitizenLifecycle.ACTIVE && record.villageId().equals(villageId)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public int citizenCount() {
        assertServerThread();
        return savedData.isAvailable() ? savedData.stateSnapshot().citizens().size() : 0;
    }

    @Override
    public CitizenCommitResult create(CitizenAggregate citizen) {
        assertServerThread();
        Objects.requireNonNull(citizen, "citizen");
        if (!savedData.isAvailable()) {
            return new CitizenCommitResult.Rejected(RepositoryRejection.PERSISTENCE_UNAVAILABLE);
        }
        if (citizen.revision() != 1) {
            return new CitizenCommitResult.Rejected(RepositoryRejection.REVISION_CONFLICT);
        }

        WorldState current = savedData.stateSnapshot();
        if (current.citizens().containsKey(citizen.id())) {
            return new CitizenCommitResult.Rejected(
                    RepositoryRejection.DUPLICATE_ID,
                    Optional.of(current.citizens().get(citizen.id()).toAggregate())
            );
        }
        if (!current.villages().containsKey(citizen.villageId())) {
            return new CitizenCommitResult.Rejected(RepositoryRejection.MISSING_RECORD);
        }
        if (current.citizens().size() >= PersistenceBounds.MAX_CITIZENS) {
            return new CitizenCommitResult.Rejected(RepositoryRejection.CAPACITY_REACHED);
        }

        DirtyKey dirtyKey = DirtyKey.citizen(citizen.id());
        if (!dirtyTracker.canAccept(dirtyKey)) {
            return new CitizenCommitResult.Rejected(RepositoryRejection.CAPACITY_REACHED);
        }
        long nextDataRevision = nextDataRevision(current);
        if (nextDataRevision < 0) {
            return new CitizenCommitResult.Rejected(RepositoryRejection.DATA_REVISION_EXHAUSTED);
        }

        savedData.replaceState(current.withCitizen(CitizenPersistentRecord.fromAggregate(citizen), nextDataRevision));
        dirtyTracker.mark(dirtyKey, DirtyReason.CREATED);
        return new CitizenCommitResult.Committed(citizen);
    }

    @Override
    public CitizenCommitResult update(CitizenAggregate citizen, long expectedRevision) {
        assertServerThread();
        Objects.requireNonNull(citizen, "citizen");
        if (!savedData.isAvailable()) {
            return new CitizenCommitResult.Rejected(RepositoryRejection.PERSISTENCE_UNAVAILABLE);
        }

        WorldState current = savedData.stateSnapshot();
        CitizenPersistentRecord existing = current.citizens().get(citizen.id());
        if (existing == null) {
            return new CitizenCommitResult.Rejected(RepositoryRejection.MISSING_RECORD);
        }
        if (existing.revision() != expectedRevision
                || expectedRevision == Long.MAX_VALUE
                || citizen.revision() != expectedRevision + 1) {
            return new CitizenCommitResult.Rejected(
                    RepositoryRejection.REVISION_CONFLICT,
                    Optional.of(existing.toAggregate())
            );
        }

        DirtyKey dirtyKey = DirtyKey.citizen(citizen.id());
        if (!dirtyTracker.canAccept(dirtyKey)) {
            return new CitizenCommitResult.Rejected(RepositoryRejection.CAPACITY_REACHED);
        }
        long nextDataRevision = nextDataRevision(current);
        if (nextDataRevision < 0) {
            return new CitizenCommitResult.Rejected(RepositoryRejection.DATA_REVISION_EXHAUSTED);
        }

        savedData.replaceState(current.withCitizen(existing.withAggregate(citizen), nextDataRevision));
        dirtyTracker.mark(dirtyKey, DirtyReason.UPDATED);
        return new CitizenCommitResult.Committed(citizen);
    }

    private long nextDataRevision(WorldState current) {
        return current.dataRevision() == Long.MAX_VALUE ? -1 : current.dataRevision() + 1;
    }

    private void assertServerThread() {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Citizen repository may only be accessed on the logical server thread");
        }
    }
}
