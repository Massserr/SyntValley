package dev.syntvalley.persistence.saveddata;

import dev.syntvalley.application.port.ProjectStateRepository;
import dev.syntvalley.domain.project.BuildProject;
import dev.syntvalley.domain.project.ProjectId;
import dev.syntvalley.persistence.dirty.DirtyKey;
import dev.syntvalley.persistence.dirty.DirtyReason;
import dev.syntvalley.persistence.dirty.DirtyTracker;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;

/** Server-thread adapter from build-project repository operations to the current SavedData root. */
public final class SavedDataProjectRepository implements ProjectStateRepository {
    private final MinecraftServer server;
    private final SyntValleySavedData savedData;
    private final DirtyTracker dirtyTracker;

    public SavedDataProjectRepository(MinecraftServer server, SyntValleySavedData savedData, DirtyTracker dirtyTracker) {
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
    public Optional<BuildProject> find(ProjectId id) {
        assertServerThread();
        Objects.requireNonNull(id, "id");
        if (!savedData.isAvailable()) {
            return Optional.empty();
        }
        return Optional.ofNullable(savedData.stateSnapshot().projects().get(id))
                .map(ProjectPersistentRecord::toAggregate);
    }

    @Override
    public List<BuildProject> all() {
        assertServerThread();
        if (!savedData.isAvailable()) {
            return List.of();
        }
        List<BuildProject> result = new ArrayList<>();
        for (ProjectPersistentRecord record : savedData.stateSnapshot().projects().values()) {
            result.add(record.toAggregate());
        }
        return List.copyOf(result);
    }

    @Override
    public boolean create(BuildProject project) {
        assertServerThread();
        Objects.requireNonNull(project, "project");
        if (!savedData.isAvailable() || project.revision() != 1) {
            return false;
        }
        WorldState current = savedData.stateSnapshot();
        if (current.projects().containsKey(project.id())
                || !current.villages().containsKey(project.villageId())
                || current.projects().size() >= PersistenceBounds.MAX_PROJECTS) {
            return false;
        }
        DirtyKey dirtyKey = DirtyKey.project(project.id());
        if (!dirtyTracker.canAccept(dirtyKey)) {
            return false;
        }
        long nextDataRevision = nextDataRevision(current);
        if (nextDataRevision < 0) {
            return false;
        }
        savedData.replaceState(current.withProject(ProjectPersistentRecord.fromAggregate(project), nextDataRevision));
        dirtyTracker.mark(dirtyKey, DirtyReason.CREATED);
        return true;
    }

    @Override
    public boolean update(BuildProject project, long expectedRevision) {
        assertServerThread();
        Objects.requireNonNull(project, "project");
        if (!savedData.isAvailable()) {
            return false;
        }
        WorldState current = savedData.stateSnapshot();
        ProjectPersistentRecord existing = current.projects().get(project.id());
        if (existing == null
                || existing.revision() != expectedRevision
                || expectedRevision == Long.MAX_VALUE
                || project.revision() != expectedRevision + 1) {
            return false;
        }
        DirtyKey dirtyKey = DirtyKey.project(project.id());
        if (!dirtyTracker.canAccept(dirtyKey)) {
            return false;
        }
        long nextDataRevision = nextDataRevision(current);
        if (nextDataRevision < 0) {
            return false;
        }
        savedData.replaceState(current.withProject(existing.withAggregate(project), nextDataRevision));
        dirtyTracker.mark(dirtyKey, DirtyReason.UPDATED);
        return true;
    }

    private long nextDataRevision(WorldState current) {
        return current.dataRevision() == Long.MAX_VALUE ? -1 : current.dataRevision() + 1;
    }

    private void assertServerThread() {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Project repository may only be accessed on the logical server thread");
        }
    }
}
