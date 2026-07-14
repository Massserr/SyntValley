package dev.syntvalley.persistence.saveddata;

import dev.syntvalley.domain.identity.CitizenId;
import dev.syntvalley.domain.identity.VillageId;
import dev.syntvalley.domain.project.ProjectId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable canonical in-memory schema-1 state. Slice 2 added villages; Slice 3 added citizens; Slice 8
 * adds build projects. The "projects" schema key was reserved from the start, so older saves carry an
 * empty project list and forward-fill to an empty map.
 */
public record WorldState(
        UUID worldInstanceId,
        long dataRevision,
        long createdAtEpochMs,
        long lastFlushGameTime,
        Map<VillageId, VillagePersistentRecord> villages,
        Map<CitizenId, CitizenPersistentRecord> citizens,
        Map<ProjectId, ProjectPersistentRecord> projects
) {
    public WorldState {
        Objects.requireNonNull(worldInstanceId, "worldInstanceId");
        if (dataRevision < 0 || createdAtEpochMs < 0 || lastFlushGameTime < 0) {
            throw new IllegalArgumentException("World state revisions and times must not be negative");
        }
        Objects.requireNonNull(villages, "villages");
        Objects.requireNonNull(citizens, "citizens");
        Objects.requireNonNull(projects, "projects");
        if (villages.size() > PersistenceBounds.MAX_VILLAGES) {
            throw new IllegalArgumentException("Village collection exceeds schema bound");
        }
        if (citizens.size() > PersistenceBounds.MAX_CITIZENS) {
            throw new IllegalArgumentException("Citizen collection exceeds schema bound");
        }
        if (projects.size() > PersistenceBounds.MAX_PROJECTS) {
            throw new IllegalArgumentException("Project collection exceeds schema bound");
        }

        LinkedHashMap<VillageId, VillagePersistentRecord> villageCopy = new LinkedHashMap<>();
        villages.forEach((id, record) -> {
            Objects.requireNonNull(id, "Village map key");
            Objects.requireNonNull(record, "Village map value");
            if (!id.equals(record.id())) {
                throw new IllegalArgumentException("Village map key does not match record ID");
            }
            villageCopy.put(id, record);
        });
        villages = Collections.unmodifiableMap(villageCopy);

        LinkedHashMap<CitizenId, CitizenPersistentRecord> citizenCopy = new LinkedHashMap<>();
        citizens.forEach((id, record) -> {
            Objects.requireNonNull(id, "Citizen map key");
            Objects.requireNonNull(record, "Citizen map value");
            if (!id.equals(record.id())) {
                throw new IllegalArgumentException("Citizen map key does not match record ID");
            }
            if (!villageCopy.containsKey(record.villageId())) {
                throw new IllegalArgumentException("Citizen references an unknown Village");
            }
            citizenCopy.put(id, record);
        });
        citizens = Collections.unmodifiableMap(citizenCopy);

        LinkedHashMap<ProjectId, ProjectPersistentRecord> projectCopy = new LinkedHashMap<>();
        projects.forEach((id, record) -> {
            Objects.requireNonNull(id, "Project map key");
            Objects.requireNonNull(record, "Project map value");
            if (!id.equals(record.id())) {
                throw new IllegalArgumentException("Project map key does not match record ID");
            }
            if (!villageCopy.containsKey(record.villageId())) {
                throw new IllegalArgumentException("Project references an unknown Village");
            }
            projectCopy.put(id, record);
        });
        projects = Collections.unmodifiableMap(projectCopy);
    }

    public static WorldState createNew(long epochMs) {
        return new WorldState(UUID.randomUUID(), 0, epochMs, 0, Map.of(), Map.of(), Map.of());
    }

    public WorldState withVillage(VillagePersistentRecord village, long nextDataRevision) {
        Objects.requireNonNull(village, "village");
        LinkedHashMap<VillageId, VillagePersistentRecord> updated = new LinkedHashMap<>(villages);
        updated.put(village.id(), village);
        return new WorldState(
                worldInstanceId, nextDataRevision, createdAtEpochMs, lastFlushGameTime, updated, citizens, projects);
    }

    public WorldState withCitizen(CitizenPersistentRecord citizen, long nextDataRevision) {
        Objects.requireNonNull(citizen, "citizen");
        LinkedHashMap<CitizenId, CitizenPersistentRecord> updated = new LinkedHashMap<>(citizens);
        updated.put(citizen.id(), citizen);
        return new WorldState(
                worldInstanceId, nextDataRevision, createdAtEpochMs, lastFlushGameTime, villages, updated, projects);
    }

    public WorldState withProject(ProjectPersistentRecord project, long nextDataRevision) {
        Objects.requireNonNull(project, "project");
        LinkedHashMap<ProjectId, ProjectPersistentRecord> updated = new LinkedHashMap<>(projects);
        updated.put(project.id(), project);
        return new WorldState(
                worldInstanceId, nextDataRevision, createdAtEpochMs, lastFlushGameTime, villages, citizens, updated);
    }

    public WorldState withLastFlushGameTime(long gameTime) {
        return new WorldState(
                worldInstanceId, dataRevision, createdAtEpochMs, gameTime, villages, citizens, projects);
    }
}
