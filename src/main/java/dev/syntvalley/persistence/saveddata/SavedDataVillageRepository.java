package dev.syntvalley.persistence.saveddata;

import dev.syntvalley.application.port.RepositoryCommitResult;
import dev.syntvalley.application.port.RepositoryRejection;
import dev.syntvalley.application.port.VillageStateRepository;
import dev.syntvalley.domain.identity.VillageId;
import dev.syntvalley.domain.village.VillageAggregate;
import dev.syntvalley.domain.village.VillageLifecycle;
import dev.syntvalley.persistence.dirty.DirtyKey;
import dev.syntvalley.persistence.dirty.DirtyReason;
import dev.syntvalley.persistence.dirty.DirtyTracker;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;

/** Server-thread adapter from application repository operations to the current SavedData root. */
public final class SavedDataVillageRepository implements VillageStateRepository {
    private final MinecraftServer server;
    private final SyntValleySavedData savedData;
    private final DirtyTracker dirtyTracker;

    public SavedDataVillageRepository(MinecraftServer server, SyntValleySavedData savedData, DirtyTracker dirtyTracker) {
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
    public Optional<VillageAggregate> find(VillageId villageId) {
        assertServerThread();
        Objects.requireNonNull(villageId, "villageId");
        if (!savedData.isAvailable()) {
            return Optional.empty();
        }
        return Optional.ofNullable(savedData.stateSnapshot().villages().get(villageId))
                .map(VillagePersistentRecord::toAggregate);
    }

    @Override
    public int villageCount() {
        assertServerThread();
        return savedData.isAvailable() ? savedData.stateSnapshot().villages().size() : 0;
    }

    @Override
    public RepositoryCommitResult create(VillageAggregate village) {
        assertServerThread();
        Objects.requireNonNull(village, "village");
        if (!savedData.isAvailable()) {
            return new RepositoryCommitResult.Rejected(RepositoryRejection.PERSISTENCE_UNAVAILABLE);
        }
        if (village.revision() != 1) {
            return new RepositoryCommitResult.Rejected(RepositoryRejection.REVISION_CONFLICT);
        }

        WorldState current = savedData.stateSnapshot();
        if (current.villages().containsKey(village.id())) {
            return new RepositoryCommitResult.Rejected(
                    RepositoryRejection.DUPLICATE_ID,
                    Optional.of(current.villages().get(village.id()).toAggregate())
            );
        }
        if (current.villages().size() >= PersistenceBounds.MAX_VILLAGES) {
            return new RepositoryCommitResult.Rejected(RepositoryRejection.CAPACITY_REACHED);
        }

        DirtyKey dirtyKey = DirtyKey.village(village.id());
        if (!dirtyTracker.canAccept(dirtyKey)) {
            return new RepositoryCommitResult.Rejected(RepositoryRejection.CAPACITY_REACHED);
        }

        long nextDataRevision = nextDataRevision(current);
        if (nextDataRevision < 0) {
            return new RepositoryCommitResult.Rejected(RepositoryRejection.DATA_REVISION_EXHAUSTED);
        }

        savedData.replaceState(current.withVillage(VillagePersistentRecord.fromNewAggregate(village), nextDataRevision));
        dirtyTracker.mark(dirtyKey, DirtyReason.CREATED);
        return new RepositoryCommitResult.Committed(village);
    }

    @Override
    public RepositoryCommitResult update(VillageAggregate village, long expectedRevision) {
        assertServerThread();
        Objects.requireNonNull(village, "village");
        if (!savedData.isAvailable()) {
            return new RepositoryCommitResult.Rejected(RepositoryRejection.PERSISTENCE_UNAVAILABLE);
        }

        WorldState current = savedData.stateSnapshot();
        VillagePersistentRecord existing = current.villages().get(village.id());
        if (existing == null) {
            return new RepositoryCommitResult.Rejected(RepositoryRejection.MISSING_RECORD);
        }
        if (existing.revision() != expectedRevision
                || expectedRevision == Long.MAX_VALUE
                || village.revision() != expectedRevision + 1) {
            return new RepositoryCommitResult.Rejected(
                    RepositoryRejection.REVISION_CONFLICT,
                    Optional.of(existing.toAggregate())
            );
        }

        DirtyKey dirtyKey = DirtyKey.village(village.id());
        if (!dirtyTracker.canAccept(dirtyKey)) {
            return new RepositoryCommitResult.Rejected(RepositoryRejection.CAPACITY_REACHED);
        }
        long nextDataRevision = nextDataRevision(current);
        if (nextDataRevision < 0) {
            return new RepositoryCommitResult.Rejected(RepositoryRejection.DATA_REVISION_EXHAUSTED);
        }

        DirtyReason reason = classifyReason(existing.toAggregate(), village);
        savedData.replaceState(current.withVillage(existing.withAggregate(village), nextDataRevision));
        dirtyTracker.mark(dirtyKey, reason);
        return new RepositoryCommitResult.Committed(village);
    }

    private long nextDataRevision(WorldState current) {
        return current.dataRevision() == Long.MAX_VALUE ? -1 : current.dataRevision() + 1;
    }

    private static DirtyReason classifyReason(VillageAggregate before, VillageAggregate after) {
        if (before.lifecycle() != VillageLifecycle.ORPHANED && after.lifecycle() == VillageLifecycle.ORPHANED) {
            return DirtyReason.CORE_ORPHANED;
        }
        if (!before.coreLocation().equals(after.coreLocation())
                || before.lastBindingGeneration() != after.lastBindingGeneration()) {
            return DirtyReason.CORE_BOUND;
        }
        return DirtyReason.UPDATED;
    }

    private void assertServerThread() {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Village repository may only be accessed on the logical server thread");
        }
    }
}
