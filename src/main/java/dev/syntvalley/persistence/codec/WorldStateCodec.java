package dev.syntvalley.persistence.codec;

import dev.syntvalley.domain.building.BuildingTemplateId;
import dev.syntvalley.domain.building.Rotation;
import dev.syntvalley.domain.building.SitePlacement;
import dev.syntvalley.domain.citizen.CitizenConstraints;
import dev.syntvalley.domain.citizen.CitizenLifecycle;
import dev.syntvalley.domain.decision.DecisionKind;
import dev.syntvalley.domain.decision.DecisionRecord;
import dev.syntvalley.domain.decision.DecisionSource;
import dev.syntvalley.domain.identity.CitizenId;
import dev.syntvalley.domain.identity.TaskId;
import dev.syntvalley.domain.identity.VillageId;
import dev.syntvalley.domain.memory.MemoryKind;
import dev.syntvalley.domain.memory.MemoryRecord;
import dev.syntvalley.domain.memory.MemorySource;
import dev.syntvalley.domain.need.NeedBounds;
import dev.syntvalley.domain.need.Needs;
import dev.syntvalley.domain.profession.CitizenProfession;
import dev.syntvalley.domain.profession.ProfessionId;
import dev.syntvalley.domain.project.ProjectId;
import dev.syntvalley.domain.project.ProjectPauseReason;
import dev.syntvalley.domain.project.ProjectState;
import dev.syntvalley.domain.task.Task;
import dev.syntvalley.domain.task.TaskFailureReason;
import dev.syntvalley.domain.task.TaskKind;
import dev.syntvalley.domain.task.TaskState;
import dev.syntvalley.domain.village.CoreLocation;
import dev.syntvalley.domain.village.VillageConstraints;
import dev.syntvalley.domain.village.VillageLifecycle;
import dev.syntvalley.persistence.migration.MigrationRegistry;
import dev.syntvalley.persistence.saveddata.CitizenPersistentRecord;
import dev.syntvalley.persistence.saveddata.PersistenceBounds;
import dev.syntvalley.persistence.saveddata.ProjectPersistentRecord;
import dev.syntvalley.persistence.saveddata.VillagePersistentRecord;
import dev.syntvalley.persistence.saveddata.VillagePolicyRecord;
import dev.syntvalley.persistence.saveddata.VillagePrioritiesRecord;
import dev.syntvalley.persistence.saveddata.WorldState;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Strict explicit schema-1 NBT codec. Unsupported data is rejected instead of truncated. */
public final class WorldStateCodec {
    private static final Set<String> ROOT_KEYS = Set.of(
            "schema_version",
            "world_instance_id",
            "data_revision",
            "created_at_epoch_ms",
            "last_flush_game_time",
            "villages",
            "citizens",
            "tasks",
            "projects",
            "memories",
            "decisions"
    );
    private static final Set<String> VILLAGE_KEYS = Set.of(
            "id",
            "revision",
            "name",
            "lifecycle",
            "last_binding_generation",
            "core",
            "created_game_time",
            "last_simulated_game_time",
            "resident_ids",
            "task_ids",
            "project_ids",
            "memory_ids",
            "priorities",
            "policy",
            "known_zones",
            "alerts"
    );
    private static final Set<String> CORE_KEYS = Set.of("dimension", "pos", "binding_generation");
    private static final Set<String> PRIORITY_KEYS = Set.of("food", "safety", "materials", "housing", "tools", "social");
    private static final Set<String> POLICY_KEYS = Set.of("auto_accept_safe_projects", "max_active_projects");
    private static final Set<String> CITIZEN_KEYS = Set.of(
            "id",
            "village_id",
            "revision",
            "lifecycle",
            "name",
            "binding_generation",
            "bound_entity_id",
            "created_game_time",
            "needs",
            "active_task",
            "profession"
    );
    private static final Set<String> NEEDS_KEYS = Set.of("last_updated_game_time", "hunger", "rest");
    private static final Set<String> PROFESSION_KEYS =
            Set.of("definition_id", "level", "experience", "changed_game_time");
    private static final Set<String> TASK_KEYS = Set.of(
            "id",
            "kind",
            "state",
            "attempt",
            "created_game_time",
            "lease_expiry_game_time",
            "failure_reason",
            "not_before_game_time"
    );
    private static final Set<String> PROJECT_KEYS = Set.of(
            "id",
            "village_id",
            "template_id",
            "template_version",
            "placement",
            "state",
            "placed_blocks",
            "total_blocks",
            "pause_reason",
            "revision"
    );
    private static final Set<String> PLACEMENT_KEYS = Set.of("dimension", "x", "y", "z", "rotation", "plan_hash");
    private static final Set<String> VILLAGE_LOG_KEYS = Set.of("village_id", "records");
    private static final Set<String> MEMORY_KEYS =
            Set.of("key", "kind", "source", "subject", "salience", "created_game_time", "pinned");
    private static final Set<String> DECISION_KEYS =
            Set.of("sequence", "kind", "subject", "chosen", "source", "reason", "game_time");

    private WorldStateCodec() {
    }

    public static CompoundTag encode(WorldState state) {
        return encodeInto(new CompoundTag(), state);
    }

    public static CompoundTag encodeInto(CompoundTag root, WorldState state) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(state, "state");

        root.putInt("schema_version", MigrationRegistry.CURRENT_SCHEMA_VERSION);
        root.putUUID("world_instance_id", state.worldInstanceId());
        root.putLong("data_revision", state.dataRevision());
        root.putLong("created_at_epoch_ms", state.createdAtEpochMs());
        root.putLong("last_flush_game_time", state.lastFlushGameTime());

        ListTag villages = new ListTag();
        state.villages().values().stream()
                .sorted((left, right) -> left.id().toString().compareTo(right.id().toString()))
                .map(WorldStateCodec::encodeVillage)
                .forEach(villages::add);
        root.put("villages", villages);

        ListTag citizens = new ListTag();
        state.citizens().values().stream()
                .sorted((left, right) -> left.id().toString().compareTo(right.id().toString()))
                .map(WorldStateCodec::encodeCitizen)
                .forEach(citizens::add);
        root.put("citizens", citizens);

        root.put("tasks", new ListTag());
        ListTag projects = new ListTag();
        state.projects().values().stream()
                .sorted((left, right) -> left.id().toString().compareTo(right.id().toString()))
                .map(WorldStateCodec::encodeProject)
                .forEach(projects::add);
        root.put("projects", projects);

        ListTag memories = new ListTag();
        state.memories().entrySet().stream()
                .sorted((left, right) -> left.getKey().toString().compareTo(right.getKey().toString()))
                .map(entry -> encodeVillageMemories(entry.getKey(), entry.getValue()))
                .forEach(memories::add);
        root.put("memories", memories);

        ListTag decisions = new ListTag();
        state.decisions().entrySet().stream()
                .sorted((left, right) -> left.getKey().toString().compareTo(right.getKey().toString()))
                .map(entry -> encodeVillageDecisions(entry.getKey(), entry.getValue()))
                .forEach(decisions::add);
        root.put("decisions", decisions);
        return root;
    }

    public static WorldState decode(CompoundTag root) {
        Objects.requireNonNull(root, "root");
        requireNoUnknownKeys(root, ROOT_KEYS, "root");

        int schemaVersion = requireInt(root, "schema_version", "schema_version");
        if (schemaVersion != MigrationRegistry.CURRENT_SCHEMA_VERSION) {
            if (schemaVersion > MigrationRegistry.CURRENT_SCHEMA_VERSION) {
                throw new UnsupportedSchemaVersionException(schemaVersion, MigrationRegistry.CURRENT_SCHEMA_VERSION);
            }
            throw new PersistenceException("schema_version", "root was not migrated to current schema");
        }
        if (!root.hasUUID("world_instance_id")) {
            throw new PersistenceException("world_instance_id", "missing or invalid UUID representation");
        }

        long dataRevision = requireNonNegativeLong(root, "data_revision", "data_revision");
        long createdAtEpochMs = requireNonNegativeLong(root, "created_at_epoch_ms", "created_at_epoch_ms");
        long lastFlushGameTime = requireNonNegativeLong(root, "last_flush_game_time", "last_flush_game_time");

        ListTag villagesTag = requireCompoundList(root, "villages", "villages");
        if (villagesTag.size() > PersistenceBounds.MAX_VILLAGES) {
            throw new PersistenceException("villages", "collection exceeds " + PersistenceBounds.MAX_VILLAGES);
        }

        LinkedHashMap<VillageId, VillagePersistentRecord> villages = new LinkedHashMap<>();
        for (int index = 0; index < villagesTag.size(); index++) {
            VillagePersistentRecord village = decodeVillage(villagesTag.getCompound(index), "villages[" + index + "]");
            if (villages.putIfAbsent(village.id(), village) != null) {
                throw new PersistenceException("villages[" + index + "].id", "duplicate Village ID");
            }
        }

        ListTag citizensTag = requireCompoundList(root, "citizens", "citizens");
        if (citizensTag.size() > PersistenceBounds.MAX_CITIZENS) {
            throw new PersistenceException("citizens", "collection exceeds " + PersistenceBounds.MAX_CITIZENS);
        }
        LinkedHashMap<CitizenId, CitizenPersistentRecord> citizens = new LinkedHashMap<>();
        for (int index = 0; index < citizensTag.size(); index++) {
            CitizenPersistentRecord citizen = decodeCitizen(citizensTag.getCompound(index), "citizens[" + index + "]");
            if (citizens.putIfAbsent(citizen.id(), citizen) != null) {
                throw new PersistenceException("citizens[" + index + "].id", "duplicate Citizen ID");
            }
        }

        requireEmptyList(root, "tasks", "tasks");

        ListTag projectsTag = requireCompoundList(root, "projects", "projects");
        if (projectsTag.size() > PersistenceBounds.MAX_PROJECTS) {
            throw new PersistenceException("projects", "collection exceeds " + PersistenceBounds.MAX_PROJECTS);
        }
        LinkedHashMap<ProjectId, ProjectPersistentRecord> projects = new LinkedHashMap<>();
        for (int index = 0; index < projectsTag.size(); index++) {
            ProjectPersistentRecord project = decodeProject(projectsTag.getCompound(index), "projects[" + index + "]");
            if (projects.putIfAbsent(project.id(), project) != null) {
                throw new PersistenceException("projects[" + index + "].id", "duplicate Project ID");
            }
        }

        ListTag memoriesTag = requireCompoundList(root, "memories", "memories");
        LinkedHashMap<VillageId, List<MemoryRecord>> memories = new LinkedHashMap<>();
        for (int index = 0; index < memoriesTag.size(); index++) {
            String path = "memories[" + index + "]";
            CompoundTag entry = memoriesTag.getCompound(index);
            requireNoUnknownKeys(entry, VILLAGE_LOG_KEYS, path);
            if (!entry.hasUUID("village_id")) {
                throw new PersistenceException(path + ".village_id", "missing or invalid UUID representation");
            }
            VillageId owner = new VillageId(entry.getUUID("village_id"));
            ListTag recordsTag = requireCompoundList(entry, "records", path + ".records");
            if (recordsTag.size() > PersistenceBounds.MAX_MEMORIES_PER_VILLAGE) {
                throw new PersistenceException(path + ".records",
                        "collection exceeds " + PersistenceBounds.MAX_MEMORIES_PER_VILLAGE);
            }
            List<MemoryRecord> records = new java.util.ArrayList<>(recordsTag.size());
            for (int r = 0; r < recordsTag.size(); r++) {
                records.add(decodeMemory(recordsTag.getCompound(r), path + ".records[" + r + "]"));
            }
            if (memories.putIfAbsent(owner, List.copyOf(records)) != null) {
                throw new PersistenceException(path + ".village_id", "duplicate Village ID");
            }
        }

        ListTag decisionsTag = requireCompoundList(root, "decisions", "decisions");
        LinkedHashMap<VillageId, List<DecisionRecord>> decisions = new LinkedHashMap<>();
        for (int index = 0; index < decisionsTag.size(); index++) {
            String path = "decisions[" + index + "]";
            CompoundTag entry = decisionsTag.getCompound(index);
            requireNoUnknownKeys(entry, VILLAGE_LOG_KEYS, path);
            if (!entry.hasUUID("village_id")) {
                throw new PersistenceException(path + ".village_id", "missing or invalid UUID representation");
            }
            VillageId owner = new VillageId(entry.getUUID("village_id"));
            ListTag recordsTag = requireCompoundList(entry, "records", path + ".records");
            if (recordsTag.size() > PersistenceBounds.MAX_DECISIONS_PER_VILLAGE) {
                throw new PersistenceException(path + ".records",
                        "collection exceeds " + PersistenceBounds.MAX_DECISIONS_PER_VILLAGE);
            }
            List<DecisionRecord> records = new java.util.ArrayList<>(recordsTag.size());
            for (int r = 0; r < recordsTag.size(); r++) {
                records.add(decodeDecision(recordsTag.getCompound(r), path + ".records[" + r + "]"));
            }
            if (decisions.putIfAbsent(owner, List.copyOf(records)) != null) {
                throw new PersistenceException(path + ".village_id", "duplicate Village ID");
            }
        }

        try {
            return new WorldState(
                    root.getUUID("world_instance_id"),
                    dataRevision,
                    createdAtEpochMs,
                    lastFlushGameTime,
                    villages,
                    citizens,
                    projects,
                    memories,
                    decisions
            );
        } catch (IllegalArgumentException exception) {
            throw new PersistenceException("root", exception.getMessage());
        }
    }

    private static CompoundTag encodeVillageMemories(VillageId villageId, List<MemoryRecord> records) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("village_id", villageId.value());
        ListTag list = new ListTag();
        for (MemoryRecord record : records) {
            CompoundTag entry = new CompoundTag();
            entry.putString("key", record.dedupeKey());
            entry.putString("kind", record.kind().name());
            entry.putString("source", record.source().name());
            entry.putString("subject", record.subject());
            entry.putInt("salience", record.salience());
            entry.putLong("created_game_time", record.createdGameTime());
            entry.putBoolean("pinned", record.pinned());
            list.add(entry);
        }
        tag.put("records", list);
        return tag;
    }

    private static MemoryRecord decodeMemory(CompoundTag tag, String path) {
        requireNoUnknownKeys(tag, MEMORY_KEYS, path);
        String key = requireBoundedString(tag, "key", path + ".key");
        MemoryKind kind;
        try {
            kind = MemoryKind.valueOf(requireString(tag, "kind", path + ".kind"));
        } catch (IllegalArgumentException exception) {
            throw new PersistenceException(path + ".kind", "unknown memory kind");
        }
        MemorySource source;
        try {
            source = MemorySource.valueOf(requireString(tag, "source", path + ".source"));
        } catch (IllegalArgumentException exception) {
            throw new PersistenceException(path + ".source", "unknown memory source");
        }
        String subject = requireBoundedString(tag, "subject", path + ".subject");
        int salience = requireBoundedInt(
                tag, "salience", path + ".salience", MemoryRecord.MIN_SALIENCE, MemoryRecord.MAX_SALIENCE);
        long created = requireNonNegativeLong(tag, "created_game_time", path + ".created_game_time");
        if (!tag.contains("pinned", Tag.TAG_BYTE)) {
            throw new PersistenceException(path + ".pinned", "missing or wrong NBT type");
        }
        try {
            return new MemoryRecord(key, kind, source, subject, salience, created, tag.getBoolean("pinned"));
        } catch (IllegalArgumentException exception) {
            throw new PersistenceException(path, exception.getMessage());
        }
    }

    private static CompoundTag encodeVillageDecisions(VillageId villageId, List<DecisionRecord> records) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("village_id", villageId.value());
        ListTag list = new ListTag();
        for (DecisionRecord record : records) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("sequence", record.sequence());
            entry.putString("kind", record.kind().name());
            entry.putString("subject", record.subject());
            entry.putString("chosen", record.chosen());
            entry.putString("source", record.source().name());
            entry.putString("reason", record.reason());
            entry.putLong("game_time", record.gameTime());
            list.add(entry);
        }
        tag.put("records", list);
        return tag;
    }

    private static DecisionRecord decodeDecision(CompoundTag tag, String path) {
        requireNoUnknownKeys(tag, DECISION_KEYS, path);
        long sequence = requirePositiveLong(tag, "sequence", path + ".sequence");
        DecisionKind kind;
        try {
            kind = DecisionKind.valueOf(requireString(tag, "kind", path + ".kind"));
        } catch (IllegalArgumentException exception) {
            throw new PersistenceException(path + ".kind", "unknown decision kind");
        }
        DecisionSource source;
        try {
            source = DecisionSource.valueOf(requireString(tag, "source", path + ".source"));
        } catch (IllegalArgumentException exception) {
            throw new PersistenceException(path + ".source", "unknown decision source");
        }
        String subject = requireBoundedString(tag, "subject", path + ".subject");
        String chosen = requireBoundedString(tag, "chosen", path + ".chosen");
        String reason = requireBoundedString(tag, "reason", path + ".reason");
        long gameTime = requireNonNegativeLong(tag, "game_time", path + ".game_time");
        try {
            return new DecisionRecord(sequence, kind, subject, chosen, source, reason, gameTime);
        } catch (IllegalArgumentException exception) {
            throw new PersistenceException(path, exception.getMessage());
        }
    }

    private static String requireBoundedString(CompoundTag tag, String key, String path) {
        String value = requireString(tag, key, path);
        if (value.length() > PersistenceBounds.MAX_LOG_TEXT_CHARACTERS) {
            throw new PersistenceException(path, "exceeds " + PersistenceBounds.MAX_LOG_TEXT_CHARACTERS + " characters");
        }
        return value;
    }

    private static CompoundTag encodeVillage(VillagePersistentRecord village) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", village.id().value());
        tag.putLong("revision", village.revision());
        tag.putString("name", village.name());
        tag.putString("lifecycle", village.lifecycle().name());
        tag.putInt("last_binding_generation", village.lastBindingGeneration());
        village.coreLocation().ifPresent(coreLocation -> {
            CompoundTag core = new CompoundTag();
            core.putString("dimension", coreLocation.dimensionId());
            core.putLong("pos", coreLocation.packedPos());
            core.putInt("binding_generation", village.lastBindingGeneration());
            tag.put("core", core);
        });
        tag.putLong("created_game_time", village.createdGameTime());
        tag.putLong("last_simulated_game_time", village.lastSimulatedGameTime());

        tag.put("resident_ids", new ListTag());
        tag.put("task_ids", new ListTag());
        tag.put("project_ids", new ListTag());
        tag.put("memory_ids", new ListTag());

        VillagePrioritiesRecord priorities = village.priorities();
        CompoundTag prioritiesTag = new CompoundTag();
        prioritiesTag.putInt("food", priorities.food());
        prioritiesTag.putInt("safety", priorities.safety());
        prioritiesTag.putInt("materials", priorities.materials());
        prioritiesTag.putInt("housing", priorities.housing());
        prioritiesTag.putInt("tools", priorities.tools());
        prioritiesTag.putInt("social", priorities.social());
        tag.put("priorities", prioritiesTag);

        VillagePolicyRecord policy = village.policy();
        CompoundTag policyTag = new CompoundTag();
        policyTag.putBoolean("auto_accept_safe_projects", policy.autoAcceptSafeProjects());
        policyTag.putInt("max_active_projects", policy.maxActiveProjects());
        tag.put("policy", policyTag);

        tag.put("known_zones", new ListTag());
        tag.put("alerts", new ListTag());
        return tag;
    }

    private static VillagePersistentRecord decodeVillage(CompoundTag tag, String path) {
        requireNoUnknownKeys(tag, VILLAGE_KEYS, path);
        if (!tag.hasUUID("id")) {
            throw new PersistenceException(path + ".id", "missing or invalid UUID representation");
        }

        long revision = requirePositiveLong(tag, "revision", path + ".revision");
        String name = requireString(tag, "name", path + ".name");
        if (!VillageConstraints.isValidName(name)) {
            throw new PersistenceException(path + ".name", "invalid or exceeds code-point bound");
        }

        VillageLifecycle lifecycle;
        String lifecycleName = requireString(tag, "lifecycle", path + ".lifecycle");
        try {
            lifecycle = VillageLifecycle.valueOf(lifecycleName);
        } catch (IllegalArgumentException exception) {
            throw new PersistenceException(path + ".lifecycle", "unknown lifecycle");
        }

        int lastBindingGeneration = requirePositiveInt(
                tag,
                "last_binding_generation",
                path + ".last_binding_generation"
        );

        java.util.Optional<CoreLocation> coreLocation = java.util.Optional.empty();
        if (tag.contains("core")) {
            if (!tag.contains("core", Tag.TAG_COMPOUND)) {
                throw new PersistenceException(path + ".core", "wrong NBT type");
            }
            CompoundTag coreTag = tag.getCompound("core");
            requireNoUnknownKeys(coreTag, CORE_KEYS, path + ".core");
            String dimensionId = requireString(coreTag, "dimension", path + ".core.dimension");
            long packedPos = requireLong(coreTag, "pos", path + ".core.pos");
            int coreGeneration = requirePositiveInt(coreTag, "binding_generation", path + ".core.binding_generation");
            if (coreGeneration != lastBindingGeneration) {
                throw new PersistenceException(path + ".core.binding_generation", "does not match last binding generation");
            }
            try {
                coreLocation = java.util.Optional.of(new CoreLocation(dimensionId, packedPos));
            } catch (IllegalArgumentException exception) {
                throw new PersistenceException(path + ".core.dimension", exception.getMessage());
            }
        }

        long createdGameTime = requireNonNegativeLong(tag, "created_game_time", path + ".created_game_time");
        long lastSimulatedGameTime = requireNonNegativeLong(
                tag,
                "last_simulated_game_time",
                path + ".last_simulated_game_time"
        );

        requireEmptyList(tag, "resident_ids", path + ".resident_ids");
        requireEmptyList(tag, "task_ids", path + ".task_ids");
        requireEmptyList(tag, "project_ids", path + ".project_ids");
        requireEmptyList(tag, "memory_ids", path + ".memory_ids");
        requireEmptyList(tag, "known_zones", path + ".known_zones");
        requireEmptyList(tag, "alerts", path + ".alerts");

        CompoundTag prioritiesTag = requireCompound(tag, "priorities", path + ".priorities");
        requireNoUnknownKeys(prioritiesTag, PRIORITY_KEYS, path + ".priorities");
        VillagePrioritiesRecord priorities = new VillagePrioritiesRecord(
                requireBoundedInt(prioritiesTag, "food", path + ".priorities.food", 0, 100),
                requireBoundedInt(prioritiesTag, "safety", path + ".priorities.safety", 0, 100),
                requireBoundedInt(prioritiesTag, "materials", path + ".priorities.materials", 0, 100),
                requireBoundedInt(prioritiesTag, "housing", path + ".priorities.housing", 0, 100),
                requireBoundedInt(prioritiesTag, "tools", path + ".priorities.tools", 0, 100),
                requireBoundedInt(prioritiesTag, "social", path + ".priorities.social", 0, 100)
        );

        CompoundTag policyTag = requireCompound(tag, "policy", path + ".policy");
        requireNoUnknownKeys(policyTag, POLICY_KEYS, path + ".policy");
        if (!policyTag.contains("auto_accept_safe_projects", Tag.TAG_BYTE)) {
            throw new PersistenceException(path + ".policy.auto_accept_safe_projects", "missing or wrong NBT type");
        }
        VillagePolicyRecord policy = new VillagePolicyRecord(
                policyTag.getBoolean("auto_accept_safe_projects"),
                requireBoundedInt(
                        policyTag,
                        "max_active_projects",
                        path + ".policy.max_active_projects",
                        1,
                        PersistenceBounds.MAX_ACTIVE_PROJECTS
                )
        );

        try {
            return new VillagePersistentRecord(
                    new VillageId(tag.getUUID("id")),
                    revision,
                    name,
                    lifecycle,
                    coreLocation,
                    lastBindingGeneration,
                    createdGameTime,
                    lastSimulatedGameTime,
                    priorities,
                    policy
            );
        } catch (IllegalArgumentException exception) {
            throw new PersistenceException(path, exception.getMessage());
        }
    }

    private static CompoundTag encodeCitizen(CitizenPersistentRecord citizen) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", citizen.id().value());
        tag.putUUID("village_id", citizen.villageId().value());
        tag.putLong("revision", citizen.revision());
        tag.putString("lifecycle", citizen.lifecycle().name());
        tag.putString("name", citizen.name());
        tag.putInt("binding_generation", citizen.bindingGeneration());
        citizen.boundEntityId().ifPresent(entityId -> tag.putUUID("bound_entity_id", entityId));
        tag.putLong("created_game_time", citizen.createdGameTime());
        tag.put("needs", encodeNeeds(citizen.needs()));
        citizen.activeTask().ifPresent(task -> tag.put("active_task", encodeTask(task)));
        citizen.profession().ifPresent(profession -> tag.put("profession", encodeProfession(profession)));
        return tag;
    }

    private static CompoundTag encodeProfession(CitizenProfession profession) {
        CompoundTag tag = new CompoundTag();
        tag.putString("definition_id", profession.professionId().value());
        tag.putInt("level", profession.level());
        tag.putLong("experience", profession.experience());
        tag.putLong("changed_game_time", profession.changedGameTime());
        return tag;
    }

    private static CompoundTag encodeNeeds(Needs needs) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("last_updated_game_time", needs.lastUpdatedGameTime());
        tag.putInt("hunger", needs.hunger());
        tag.putInt("rest", needs.rest());
        return tag;
    }

    private static CompoundTag encodeTask(Task task) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", task.id().value());
        tag.putString("kind", task.kind().name());
        tag.putString("state", task.state().name());
        tag.putInt("attempt", task.attempt());
        tag.putLong("created_game_time", task.createdGameTime());
        tag.putLong("lease_expiry_game_time", task.leaseExpiryGameTime());
        task.failureReason().ifPresent(reason -> tag.putString("failure_reason", reason.name()));
        tag.putLong("not_before_game_time", task.notBeforeGameTime());
        return tag;
    }

    private static CitizenPersistentRecord decodeCitizen(CompoundTag tag, String path) {
        requireNoUnknownKeys(tag, CITIZEN_KEYS, path);
        if (!tag.hasUUID("id")) {
            throw new PersistenceException(path + ".id", "missing or invalid UUID representation");
        }
        if (!tag.hasUUID("village_id")) {
            throw new PersistenceException(path + ".village_id", "missing or invalid UUID representation");
        }

        long revision = requirePositiveLong(tag, "revision", path + ".revision");

        CitizenLifecycle lifecycle;
        String lifecycleName = requireString(tag, "lifecycle", path + ".lifecycle");
        try {
            lifecycle = CitizenLifecycle.valueOf(lifecycleName);
        } catch (IllegalArgumentException exception) {
            throw new PersistenceException(path + ".lifecycle", "unknown lifecycle");
        }

        String name = requireString(tag, "name", path + ".name");
        if (!CitizenConstraints.isValidName(name)) {
            throw new PersistenceException(path + ".name", "invalid or exceeds code-point bound");
        }

        int bindingGeneration = requirePositiveInt(tag, "binding_generation", path + ".binding_generation");

        Optional<UUID> boundEntityId = Optional.empty();
        if (tag.contains("bound_entity_id")) {
            if (!tag.hasUUID("bound_entity_id")) {
                throw new PersistenceException(path + ".bound_entity_id", "missing or invalid UUID representation");
            }
            boundEntityId = Optional.of(tag.getUUID("bound_entity_id"));
        }

        long createdGameTime = requireNonNegativeLong(tag, "created_game_time", path + ".created_game_time");

        CitizenId citizenId = new CitizenId(tag.getUUID("id"));

        Needs needs = tag.contains("needs")
                ? decodeNeeds(requireCompound(tag, "needs", path + ".needs"), path + ".needs")
                : Needs.full(createdGameTime);

        Optional<Task> activeTask = Optional.empty();
        if (tag.contains("active_task")) {
            activeTask = Optional.of(decodeTask(
                    requireCompound(tag, "active_task", path + ".active_task"), citizenId, path + ".active_task"));
        }

        Optional<CitizenProfession> profession = Optional.empty();
        if (tag.contains("profession")) {
            profession = Optional.of(decodeProfession(
                    requireCompound(tag, "profession", path + ".profession"), path + ".profession"));
        }

        try {
            return new CitizenPersistentRecord(
                    citizenId,
                    new VillageId(tag.getUUID("village_id")),
                    revision,
                    lifecycle,
                    name,
                    bindingGeneration,
                    boundEntityId,
                    createdGameTime,
                    needs,
                    activeTask,
                    profession
            );
        } catch (IllegalArgumentException exception) {
            throw new PersistenceException(path, exception.getMessage());
        }
    }

    private static CitizenProfession decodeProfession(CompoundTag tag, String path) {
        requireNoUnknownKeys(tag, PROFESSION_KEYS, path);
        String definitionId = requireString(tag, "definition_id", path + ".definition_id");
        if (!ProfessionId.isValid(definitionId)) {
            throw new PersistenceException(path + ".definition_id", "invalid profession id");
        }
        int level = requirePositiveInt(tag, "level", path + ".level");
        long experience = requireNonNegativeLong(tag, "experience", path + ".experience");
        long changedGameTime = requireNonNegativeLong(tag, "changed_game_time", path + ".changed_game_time");
        try {
            return new CitizenProfession(new ProfessionId(definitionId), level, experience, changedGameTime);
        } catch (IllegalArgumentException exception) {
            throw new PersistenceException(path, exception.getMessage());
        }
    }

    private static Needs decodeNeeds(CompoundTag tag, String path) {
        requireNoUnknownKeys(tag, NEEDS_KEYS, path);
        long lastUpdated = requireNonNegativeLong(tag, "last_updated_game_time", path + ".last_updated_game_time");
        int hunger = requireBoundedInt(tag, "hunger", path + ".hunger", NeedBounds.MIN, NeedBounds.MAX);
        int rest = requireBoundedInt(tag, "rest", path + ".rest", NeedBounds.MIN, NeedBounds.MAX);
        try {
            return new Needs(hunger, rest, lastUpdated);
        } catch (IllegalArgumentException exception) {
            throw new PersistenceException(path, exception.getMessage());
        }
    }

    private static Task decodeTask(CompoundTag tag, CitizenId citizenId, String path) {
        requireNoUnknownKeys(tag, TASK_KEYS, path);
        if (!tag.hasUUID("id")) {
            throw new PersistenceException(path + ".id", "missing or invalid UUID representation");
        }

        TaskKind kind;
        try {
            kind = TaskKind.valueOf(requireString(tag, "kind", path + ".kind"));
        } catch (IllegalArgumentException exception) {
            throw new PersistenceException(path + ".kind", "unknown task kind");
        }
        TaskState state;
        try {
            state = TaskState.valueOf(requireString(tag, "state", path + ".state"));
        } catch (IllegalArgumentException exception) {
            throw new PersistenceException(path + ".state", "unknown task state");
        }

        int attempt = requireBoundedInt(tag, "attempt", path + ".attempt", 0, Integer.MAX_VALUE);
        long created = requireNonNegativeLong(tag, "created_game_time", path + ".created_game_time");
        long lease = requireNonNegativeLong(tag, "lease_expiry_game_time", path + ".lease_expiry_game_time");
        long notBefore = requireNonNegativeLong(tag, "not_before_game_time", path + ".not_before_game_time");

        Optional<TaskFailureReason> failureReason = Optional.empty();
        if (tag.contains("failure_reason")) {
            try {
                failureReason = Optional.of(
                        TaskFailureReason.valueOf(requireString(tag, "failure_reason", path + ".failure_reason")));
            } catch (IllegalArgumentException exception) {
                throw new PersistenceException(path + ".failure_reason", "unknown failure reason");
            }
        }

        try {
            return new Task(
                    new TaskId(tag.getUUID("id")), citizenId, kind, state, attempt, created, lease, failureReason,
                    notBefore);
        } catch (IllegalArgumentException exception) {
            throw new PersistenceException(path, exception.getMessage());
        }
    }

    private static CompoundTag encodeProject(ProjectPersistentRecord project) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", project.id().value());
        tag.putUUID("village_id", project.villageId().value());
        tag.putString("template_id", project.templateId().value());
        tag.putInt("template_version", project.templateVersion());

        SitePlacement placement = project.placement();
        CompoundTag placementTag = new CompoundTag();
        placementTag.putString("dimension", placement.dimensionId());
        placementTag.putInt("x", placement.originX());
        placementTag.putInt("y", placement.originY());
        placementTag.putInt("z", placement.originZ());
        placementTag.putString("rotation", placement.rotation().name());
        placementTag.putLong("plan_hash", placement.planHash());
        tag.put("placement", placementTag);

        tag.putString("state", project.state().name());
        tag.putInt("placed_blocks", project.placedBlocks());
        tag.putInt("total_blocks", project.totalBlocks());
        project.pauseReason().ifPresent(reason -> tag.putString("pause_reason", reason.name()));
        tag.putLong("revision", project.revision());
        return tag;
    }

    private static ProjectPersistentRecord decodeProject(CompoundTag tag, String path) {
        requireNoUnknownKeys(tag, PROJECT_KEYS, path);
        if (!tag.hasUUID("id")) {
            throw new PersistenceException(path + ".id", "missing or invalid UUID representation");
        }
        if (!tag.hasUUID("village_id")) {
            throw new PersistenceException(path + ".village_id", "missing or invalid UUID representation");
        }

        String templateId = requireString(tag, "template_id", path + ".template_id");
        if (!BuildingTemplateId.isValid(templateId)) {
            throw new PersistenceException(path + ".template_id", "invalid template id");
        }
        int templateVersion = requirePositiveInt(tag, "template_version", path + ".template_version");

        CompoundTag placementTag = requireCompound(tag, "placement", path + ".placement");
        requireNoUnknownKeys(placementTag, PLACEMENT_KEYS, path + ".placement");
        String dimension = requireString(placementTag, "dimension", path + ".placement.dimension");
        int x = requireInt(placementTag, "x", path + ".placement.x");
        int y = requireInt(placementTag, "y", path + ".placement.y");
        int z = requireInt(placementTag, "z", path + ".placement.z");
        Rotation rotation;
        try {
            rotation = Rotation.valueOf(requireString(placementTag, "rotation", path + ".placement.rotation"));
        } catch (IllegalArgumentException exception) {
            throw new PersistenceException(path + ".placement.rotation", "unknown rotation");
        }
        long planHash = requireLong(placementTag, "plan_hash", path + ".placement.plan_hash");
        SitePlacement placement;
        try {
            placement = new SitePlacement(dimension, x, y, z, rotation, planHash);
        } catch (IllegalArgumentException exception) {
            throw new PersistenceException(path + ".placement", exception.getMessage());
        }

        ProjectState state;
        try {
            state = ProjectState.valueOf(requireString(tag, "state", path + ".state"));
        } catch (IllegalArgumentException exception) {
            throw new PersistenceException(path + ".state", "unknown project state");
        }
        int placedBlocks = requireBoundedInt(tag, "placed_blocks", path + ".placed_blocks", 0, Integer.MAX_VALUE);
        int totalBlocks = requirePositiveInt(tag, "total_blocks", path + ".total_blocks");
        long revision = requirePositiveLong(tag, "revision", path + ".revision");

        Optional<ProjectPauseReason> pauseReason = Optional.empty();
        if (tag.contains("pause_reason")) {
            try {
                pauseReason = Optional.of(
                        ProjectPauseReason.valueOf(requireString(tag, "pause_reason", path + ".pause_reason")));
            } catch (IllegalArgumentException exception) {
                throw new PersistenceException(path + ".pause_reason", "unknown pause reason");
            }
        }

        try {
            return new ProjectPersistentRecord(
                    new ProjectId(tag.getUUID("id")),
                    new VillageId(tag.getUUID("village_id")),
                    new BuildingTemplateId(templateId),
                    templateVersion,
                    placement,
                    state,
                    placedBlocks,
                    totalBlocks,
                    pauseReason,
                    revision);
        } catch (IllegalArgumentException exception) {
            throw new PersistenceException(path, exception.getMessage());
        }
    }

    private static void requireNoUnknownKeys(CompoundTag tag, Set<String> allowed, String path) {
        for (String key : tag.getAllKeys()) {
            if (!allowed.contains(key)) {
                throw new PersistenceException(path + "." + key, "unknown field");
            }
        }
    }

    private static ListTag requireCompoundList(CompoundTag tag, String key, String path) {
        if (!tag.contains(key, Tag.TAG_LIST)) {
            throw new PersistenceException(path, "missing or wrong NBT type");
        }
        ListTag list = (ListTag) tag.get(key);
        if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) {
            throw new PersistenceException(path, "list element has wrong NBT type");
        }
        return list;
    }

    private static void requireEmptyList(CompoundTag tag, String key, String path) {
        if (!tag.contains(key, Tag.TAG_LIST)) {
            throw new PersistenceException(path, "missing or wrong NBT type");
        }
        ListTag list = (ListTag) tag.get(key);
        if (!list.isEmpty()) {
            throw new PersistenceException(path, "records are unsupported by Slice 2 and cannot be discarded");
        }
    }

    private static CompoundTag requireCompound(CompoundTag tag, String key, String path) {
        if (!tag.contains(key, Tag.TAG_COMPOUND)) {
            throw new PersistenceException(path, "missing or wrong NBT type");
        }
        return tag.getCompound(key);
    }

    private static String requireString(CompoundTag tag, String key, String path) {
        if (!tag.contains(key, Tag.TAG_STRING)) {
            throw new PersistenceException(path, "missing or wrong NBT type");
        }
        return tag.getString(key);
    }

    private static int requireInt(CompoundTag tag, String key, String path) {
        if (!tag.contains(key, Tag.TAG_INT)) {
            throw new PersistenceException(path, "missing or wrong NBT type");
        }
        return tag.getInt(key);
    }

    private static int requirePositiveInt(CompoundTag tag, String key, String path) {
        return requireBoundedInt(tag, key, path, 1, Integer.MAX_VALUE);
    }

    private static int requireBoundedInt(CompoundTag tag, String key, String path, int minimum, int maximum) {
        int value = requireInt(tag, key, path);
        if (value < minimum || value > maximum) {
            throw new PersistenceException(path, "value is outside " + minimum + ".." + maximum);
        }
        return value;
    }

    private static long requireLong(CompoundTag tag, String key, String path) {
        if (!tag.contains(key, Tag.TAG_LONG)) {
            throw new PersistenceException(path, "missing or wrong NBT type");
        }
        return tag.getLong(key);
    }

    private static long requirePositiveLong(CompoundTag tag, String key, String path) {
        long value = requireLong(tag, key, path);
        if (value < 1) {
            throw new PersistenceException(path, "value must be positive");
        }
        return value;
    }

    private static long requireNonNegativeLong(CompoundTag tag, String key, String path) {
        long value = requireLong(tag, key, path);
        if (value < 0) {
            throw new PersistenceException(path, "value must not be negative");
        }
        return value;
    }
}
