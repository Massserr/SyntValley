package dev.syntvalley.bootstrap;

import dev.syntvalley.ai.backend.LlmRequest;
import dev.syntvalley.ai.backend.LlmResult;
import dev.syntvalley.ai.ollama.OllamaConfig;
import dev.syntvalley.ai.ollama.OllamaLlmBackend;
import dev.syntvalley.ai.orchestration.BoundedLlmExecutor;
import dev.syntvalley.ai.orchestration.CircuitBreaker;
import dev.syntvalley.ai.orchestration.SubmitResult;
import dev.syntvalley.application.building.BuildingCatalog;
import dev.syntvalley.application.building.PlacementPlanner;
import dev.syntvalley.application.port.CitizenStateRepository;
import dev.syntvalley.application.port.ProjectStateRepository;
import dev.syntvalley.application.port.ResourceSource;
import dev.syntvalley.application.port.ResourceWithdrawal;
import dev.syntvalley.application.port.VillageStateRepository;
import dev.syntvalley.application.profession.ProfessionCatalog;
import dev.syntvalley.application.query.VillageLogPage;
import dev.syntvalley.application.query.VillageOverviewDto;
import dev.syntvalley.application.query.VillageOverviewQuery;
import dev.syntvalley.application.resource.NutritionCatalog;
import dev.syntvalley.application.service.CitizenApplicationService;
import dev.syntvalley.application.service.CitizenBindingService;
import dev.syntvalley.application.service.CoreBindingService;
import dev.syntvalley.application.service.MealOutcome;
import dev.syntvalley.application.service.PendingConsoleLinks;
import dev.syntvalley.application.service.ProjectApplicationService;
import dev.syntvalley.application.service.ProposalRejection;
import dev.syntvalley.application.service.ProposeResult;
import dev.syntvalley.application.service.ResourceApplicationService;
import dev.syntvalley.application.service.ScreenSessionRegistry;
import dev.syntvalley.application.service.VillageApplicationService;
import dev.syntvalley.application.simulation.CitizenSimulationStep;
import dev.syntvalley.config.SyntValleyConfig;
import dev.syntvalley.content.building.WorldPlacementAdapter;
import dev.syntvalley.domain.building.BuildingTemplateId;
import dev.syntvalley.domain.citizen.CitizenAggregate;
import dev.syntvalley.domain.citizen.CitizenEntityBinding;
import dev.syntvalley.domain.citizen.CitizenLifecycle;
import dev.syntvalley.domain.decision.DecisionKind;
import dev.syntvalley.domain.decision.DecisionLog;
import dev.syntvalley.domain.decision.DecisionRecord;
import dev.syntvalley.domain.decision.DecisionSource;
import dev.syntvalley.domain.identity.CitizenId;
import dev.syntvalley.domain.identity.TaskId;
import dev.syntvalley.domain.identity.VillageId;
import dev.syntvalley.domain.memory.MemoryKind;
import dev.syntvalley.domain.memory.MemoryRecord;
import dev.syntvalley.domain.memory.MemorySource;
import dev.syntvalley.domain.memory.MemoryStore;
import dev.syntvalley.domain.need.NeedDecayRates;
import dev.syntvalley.domain.need.NeedKind;
import dev.syntvalley.domain.need.NeedUpdatePolicy;
import dev.syntvalley.domain.need.Needs;
import dev.syntvalley.domain.profession.CitizenProfession;
import dev.syntvalley.domain.profession.ProfessionDefinition;
import dev.syntvalley.domain.profession.ProfessionId;
import dev.syntvalley.domain.project.BuildProject;
import dev.syntvalley.domain.project.ProjectId;
import dev.syntvalley.domain.resource.MealPlanner;
import dev.syntvalley.domain.resource.ResourceKey;
import dev.syntvalley.domain.resource.ResourceLedger;
import dev.syntvalley.domain.resource.ResourceReservations;
import dev.syntvalley.domain.task.Task;
import dev.syntvalley.domain.task.TaskFailureReason;
import dev.syntvalley.domain.task.TaskKind;
import dev.syntvalley.domain.task.TaskRetryPolicy;
import dev.syntvalley.domain.task.TaskState;
import dev.syntvalley.domain.task.TaskTransitionResult;
import dev.syntvalley.domain.village.CoreBinding;
import dev.syntvalley.domain.village.CoreLocation;
import dev.syntvalley.domain.village.VillageAggregate;
import dev.syntvalley.observability.SyntValleyLog;
import dev.syntvalley.persistence.dirty.DirtyTracker;
import dev.syntvalley.persistence.dirty.PersistenceCoordinator;
import dev.syntvalley.persistence.saveddata.PersistenceBounds;
import dev.syntvalley.persistence.saveddata.SavedDataCitizenRepository;
import dev.syntvalley.persistence.saveddata.SavedDataProjectRepository;
import dev.syntvalley.persistence.saveddata.SavedDataVillageLogRepository;
import dev.syntvalley.persistence.saveddata.SavedDataVillageRepository;
import dev.syntvalley.persistence.saveddata.SyntValleySavedData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Per-MinecraftServer composition root. Canonical gameplay state remains in SavedData. */
public final class SyntValleyServerRuntime {
    /** How long a "select Village for Console link" selection stays valid, in game ticks. */
    public static final long LINK_EXPIRY_TICKS = 600L;

    /** Rest points recovered per serviced simulation step while a citizen rests in place. */
    private static final int REST_RECOVERY_PER_STEP = 60;

    /** Hunger at or below which a citizen will eat a meal drawn from village storage. */
    private static final int FOOD_EAT_THRESHOLD = 600;

    /** How long a RUNNING food-delivery task holds its lease, in game ticks. */
    private static final long FOOD_TASK_LEASE_TICKS = 100L;

    /** Retry/backoff for a food task that hit a stale ledger view before it is terminally failed. */
    private static final TaskRetryPolicy FOOD_RETRY_POLICY = TaskRetryPolicy.defaults();

    /** How often active build projects advance one block, in game ticks (a steady, visible pace). */
    private static final long PROJECT_TICK_INTERVAL = 10L;

    /** Bounded page sizes for the read-only village log screen. */
    private static final int MEMORY_PAGE_SIZE = 16;
    private static final int DECISION_PAGE_SIZE = 8;

    /** Salience of freshly recorded memories, on the 0..1000 scale. */
    private static final int SALIENCE_FED = 300;
    private static final int SALIENCE_PROJECT_COMPLETED = 700;
    private static final int SALIENCE_DEATH = 800;

    /** LLM boundary constants: one local worker, small bounded inbox, per-tick drain cap. */
    private static final int AI_WORKER_COUNT = 1;
    private static final int AI_INBOX_CAPACITY = 16;
    private static final int AI_DRAIN_PER_TICK = 4;
    private static final int AI_CIRCUIT_FAILURES = 3;
    private static final long AI_CIRCUIT_COOLDOWN_MILLIS = 30_000L;
    private static final int AI_MAX_RESPONSE_CHARS = 65_536;

    private final MinecraftServer server;
    private final SyntValleySavedData savedData;
    private final VillageStateRepository villageRepository;
    private final CitizenStateRepository citizenRepository;
    private final CoreBindingService coreBindingService;
    private final CitizenApplicationService citizenService;
    private final CitizenBindingService citizenBindingService;
    private final PendingConsoleLinks pendingConsoleLinks = new PendingConsoleLinks();
    private final ScreenSessionRegistry screenSessions = new ScreenSessionRegistry();
    private final VillageOverviewQuery overviewQuery;
    private final NeedDecayRates needDecayRates = NeedDecayRates.defaults();
    private final CitizenSimulationStep simulationStep =
            new CitizenSimulationStep(needDecayRates, REST_RECOVERY_PER_STEP);
    private final ProfessionCatalog professions = ProfessionCatalog.builtin();
    private final Map<VillageId, Set<ResourceSource>> storagesByVillage = new LinkedHashMap<>();
    private final Map<VillageId, ResourceReservations> reservationsByVillage = new LinkedHashMap<>();
    private final ResourceApplicationService resourceService =
            new ResourceApplicationService(NutritionCatalog.builtin(), new MealPlanner(FOOD_EAT_THRESHOLD));
    private final ProjectApplicationService projectService =
            new ProjectApplicationService(PlacementPlanner.defaults(), ProjectId::random);
    private final ProjectStateRepository projectRepository;
    private final SavedDataVillageLogRepository villageLogRepository;
    private final Map<VillageId, MemoryStore> memoriesByVillage = new LinkedHashMap<>();
    private final Map<VillageId, DecisionLog> decisionsByVillage = new LinkedHashMap<>();
    private long overviewSnapshotSeq;
    private final PersistenceCoordinator persistenceCoordinator;
    private final BoundedLlmExecutor llmExecutor;
    private final String llmBackendName;
    private String lastAiDiagnostic = "none";
    private boolean acceptingCommands = true;

    public SyntValleyServerRuntime(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
        this.savedData = SyntValleySavedData.get(server);
        DirtyTracker dirtyTracker = new DirtyTracker(PersistenceBounds.MAX_DIRTY_KEYS, savedData::setDirty);
        this.villageRepository = new SavedDataVillageRepository(server, savedData, dirtyTracker);
        VillageApplicationService villageService = new VillageApplicationService(villageRepository, VillageId::random);
        this.coreBindingService = new CoreBindingService(villageRepository, villageService);
        this.citizenRepository = new SavedDataCitizenRepository(server, savedData, dirtyTracker);
        this.projectRepository = new SavedDataProjectRepository(server, savedData, dirtyTracker);
        this.villageLogRepository = new SavedDataVillageLogRepository(server, savedData, dirtyTracker);
        this.citizenService = new CitizenApplicationService(citizenRepository, villageRepository, CitizenId::random);
        this.citizenBindingService = new CitizenBindingService(citizenRepository);
        this.overviewQuery = new VillageOverviewQuery(villageRepository, citizenRepository);
        this.persistenceCoordinator = new PersistenceCoordinator(savedData, dirtyTracker);

        BoundedLlmExecutor executor = null;
        String backendName = "disabled";
        try {
            if (SyntValleyConfig.AI_ENABLED.get()) {
                OllamaConfig aiConfig = new OllamaConfig(
                        SyntValleyConfig.AI_BASE_URL.get(),
                        SyntValleyConfig.AI_MODEL.get(),
                        SyntValleyConfig.AI_CONNECT_TIMEOUT_MILLIS.get(),
                        SyntValleyConfig.AI_REQUEST_TIMEOUT_MILLIS.get(),
                        AI_MAX_RESPONSE_CHARS);
                OllamaLlmBackend backend = new OllamaLlmBackend(aiConfig);
                backendName = backend.name();
                executor = new BoundedLlmExecutor(
                        backend, AI_WORKER_COUNT, SyntValleyConfig.AI_QUEUE_CAPACITY.get(), AI_INBOX_CAPACITY,
                        new CircuitBreaker(AI_CIRCUIT_FAILURES, AI_CIRCUIT_COOLDOWN_MILLIS),
                        System::currentTimeMillis);
            }
        } catch (IllegalArgumentException | IllegalStateException badConfig) {
            // Bad AI config (or config not loaded) must never stop the server — run deterministically.
            SyntValleyLog.logger().error("SyntValley AI disabled by invalid configuration: {}", badConfig.getMessage());
            executor = null;
            backendName = "disabled (invalid config)";
        }
        this.llmExecutor = executor;
        this.llmBackendName = backendName;
    }

    public boolean isPersistenceAvailable() {
        return savedData.isAvailable();
    }

    public Optional<String> persistenceFailureDiagnostic() {
        return savedData.failureDiagnostic();
    }

    public CoreBindingService.EnsureBindingResult ensureCoreBound(
            Optional<CoreBinding> localBinding,
            CoreLocation location,
            long gameTime
    ) {
        assertServerThread();
        if (!acceptingCommands) {
            return new CoreBindingService.EnsureBindingResult.Rejected(
                    CoreBindingService.CoreBindingRejection.RUNTIME_STOPPING
            );
        }
        return coreBindingService.ensureBound(localBinding, location, gameTime);
    }

    public CoreBindingService.RemoveBindingResult removeCore(CoreBinding binding, CoreLocation location) {
        assertServerThread();
        if (!acceptingCommands) {
            return new CoreBindingService.RemoveBindingResult.Rejected(
                    CoreBindingService.CoreBindingRejection.RUNTIME_STOPPING
            );
        }
        return coreBindingService.remove(binding, location);
    }

    public Optional<VillageAggregate> inspectVillage(VillageId villageId) {
        assertServerThread();
        return coreBindingService.inspect(villageId);
    }

    public int villageCount() {
        assertServerThread();
        return villageRepository.villageCount();
    }

    public CitizenApplicationService.HireResult hireCitizen(VillageId villageId, String requestedName, long gameTime) {
        assertServerThread();
        if (!acceptingCommands) {
            return new CitizenApplicationService.HireResult.Rejected(
                    CitizenApplicationService.HireRejection.RUNTIME_STOPPING
            );
        }
        CitizenApplicationService.HireResult result = citizenService.hire(villageId, requestedName, gameTime);
        if (result instanceof CitizenApplicationService.HireResult.Hired hired) {
            assignProfession(hired.citizen().id(), ProfessionCatalog.CARETAKER, gameTime);
        }
        return result;
    }

    /** Assigns a catalog profession to a citizen (server-authorized). Unknown professions are rejected. */
    public boolean assignProfession(CitizenId citizenId, ProfessionId professionId, long gameTime) {
        assertServerThread();
        if (!acceptingCommands || !professions.contains(professionId)) {
            return false;
        }
        Optional<CitizenAggregate> current = citizenRepository.find(citizenId);
        if (current.isEmpty() || current.orElseThrow().lifecycle() != CitizenLifecycle.ACTIVE) {
            return false;
        }
        CitizenAggregate citizen = current.orElseThrow();
        citizenRepository.update(
                citizen.withProfession(Optional.of(CitizenProfession.assign(professionId, gameTime))),
                citizen.revision());
        return true;
    }

    public CitizenBindingService.EnsureCitizenResult ensureCitizenBound(CitizenEntityBinding binding, UUID entityId) {
        assertServerThread();
        if (!acceptingCommands) {
            return new CitizenBindingService.EnsureCitizenResult.Rejected(
                    CitizenBindingService.CitizenBindingRejection.RUNTIME_STOPPING
            );
        }
        return citizenBindingService.ensureBound(binding, entityId);
    }

    public CitizenBindingService.DeathResult recordCitizenDeath(CitizenEntityBinding binding, UUID entityId) {
        assertServerThread();
        if (!acceptingCommands) {
            return new CitizenBindingService.DeathResult.Rejected(
                    CitizenBindingService.CitizenBindingRejection.RUNTIME_STOPPING
            );
        }
        CitizenBindingService.DeathResult result = citizenBindingService.recordDeath(binding, entityId);
        if (result instanceof CitizenBindingService.DeathResult.Recorded recorded && recorded.changed()) {
            long now = server.overworld().getGameTime();
            recordMemory(binding.villageId(), new MemoryRecord(
                    "death:" + binding.citizenId(), MemoryKind.CITIZEN_DIED, MemorySource.OBSERVED,
                    recorded.citizen().name(), SALIENCE_DEATH, now, false));
        }
        return result;
    }

    public Optional<CitizenAggregate> inspectCitizen(CitizenId citizenId) {
        assertServerThread();
        return citizenService.inspect(citizenId);
    }

    public int citizenCount() {
        assertServerThread();
        return citizenRepository.citizenCount();
    }

    /** Advances one loaded citizen's needs and active task, persisting only when something changed. */
    public void simulateCitizen(CitizenId citizenId, long gameTime) {
        assertServerThread();
        if (!acceptingCommands) {
            return;
        }
        Optional<CitizenAggregate> current = citizenRepository.find(citizenId);
        if (current.isEmpty() || current.orElseThrow().lifecycle() != CitizenLifecycle.ACTIVE) {
            return;
        }
        CitizenAggregate citizen = current.orElseThrow();
        Optional<ProfessionDefinition> definition =
                citizen.profession().flatMap(active -> professions.get(active.professionId()));
        CitizenAggregate advanced =
                simulationStep.advance(citizen, gameTime, TaskId::random, definition, citizen.personality());
        CitizenAggregate result = feedFromStorageIfRequested(citizen, advanced, gameTime);
        if (result != citizen) {
            citizenRepository.update(result, citizen.revision());
            recordTaskChangeDecision(citizen, result, gameTime);
        }
    }

    /** Audits the planner's outcome whenever a citizen's activity actually changes kind. */
    private void recordTaskChangeDecision(CitizenAggregate before, CitizenAggregate after, long gameTime) {
        Optional<TaskKind> previousKind = before.activeTask().map(Task::kind);
        Optional<TaskKind> nextKind = after.activeTask().map(Task::kind);
        if (nextKind.isEmpty() || nextKind.equals(previousKind)) {
            return;
        }
        TaskKind kind = nextKind.orElseThrow();
        String reason = switch (kind) {
            case REQUEST_FOOD -> "hunger critical";
            case REST -> "rest low";
            case WORK -> "shift (diligence " + after.personality().diligence() + ")";
            case IDLE -> "idle";
        };
        recordDecision(after.villageId(), DecisionKind.CITIZEN_TASK, after.name(), kind.name(), reason, gameTime);
    }

    /**
     * When the just-advanced citizen's active task is to get food, draws one meal from the village's
     * linked storage and folds it into a SINGLE simulation step: the returned aggregate is exactly one
     * revision beyond the stored {@code original}, never two, so the optimistic update is accepted (a
     * second {@code withSimulation} would bump the revision twice and the repository would reject it). On
     * success it restores hunger and completes the task; a stale ledger view fails the task with
     * {@link TaskFailureReason#STALE_RESOURCE_VIEW} and retries with backoff; with no food available the
     * task simply waits. All physical removal goes through {@link ResourceSource#withdraw}, so nothing is
     * ever created or duplicated.
     */
    private CitizenAggregate feedFromStorageIfRequested(
            CitizenAggregate original, CitizenAggregate advanced, long gameTime) {
        Optional<Task> active = advanced.activeTask().filter(task -> !task.state().isTerminal());
        if (active.isEmpty() || active.orElseThrow().kind() != TaskKind.REQUEST_FOOD) {
            return advanced;
        }
        Task task = active.orElseThrow();

        Task running;
        if (task.state() == TaskState.RUNNING) {
            running = task;
        } else if (task.start(gameTime + FOOD_TASK_LEASE_TICKS, gameTime)
                instanceof TaskTransitionResult.Changed started) {
            running = started.task();
        } else {
            return advanced; // still in retry backoff after a stale view; keep the advanced baseline
        }

        VillageId villageId = advanced.villageId();
        ResourceReservations reservations =
                reservationsByVillage.computeIfAbsent(villageId, key -> new ResourceReservations());
        ResourceLedger ledger = buildLedger(villageId, gameTime);
        ResourceWithdrawal withdrawal = (key, amount) -> withdrawFromVillage(villageId, key, amount);

        MealOutcome outcome = resourceService.feed(
                running.id(), advanced.needs().hunger(), ledger, reservations, withdrawal, gameTime);
        // Re-derive from the stored citizen so decay + feeding together advance the revision exactly once.
        return switch (outcome.result()) {
            case FED -> original.withSimulation(
                    advanced.needs().replenish(NeedKind.HUNGER, outcome.hungerRestored()),
                    Optional.of(requireChanged(running.succeed())),
                    advanced.profession());
            case STALE -> original.withSimulation(
                    advanced.needs(),
                    Optional.of(requireChanged(
                            running.fail(TaskFailureReason.STALE_RESOURCE_VIEW, gameTime, FOOD_RETRY_POLICY))),
                    advanced.profession());
            case NO_FOOD, RESERVED -> running == task
                    ? advanced
                    : original.withSimulation(advanced.needs(), Optional.of(running), advanced.profession());
        };
    }

    /** Fans a withdrawal request across the village's linked storages, returning the total removed. */
    private int withdrawFromVillage(VillageId villageId, ResourceKey key, int amount) {
        Set<ResourceSource> sources = storagesByVillage.get(villageId);
        if (sources == null || amount <= 0) {
            return 0;
        }
        int remaining = amount;
        for (ResourceSource source : sources) {
            if (remaining <= 0) {
                break;
            }
            remaining -= source.withdraw(key, remaining);
        }
        return amount - remaining;
    }

    private static Task requireChanged(TaskTransitionResult result) {
        if (result instanceof TaskTransitionResult.Changed changed) {
            return changed.task();
        }
        throw new IllegalStateException("expected task transition to apply, got " + result);
    }

    /** Feeds a citizen: brings needs up to now, then replenishes hunger. Returns whether it applied. */
    public boolean feedCitizen(CitizenId citizenId, int hungerAmount, long gameTime) {
        assertServerThread();
        if (!acceptingCommands || hungerAmount <= 0) {
            return false;
        }
        Optional<CitizenAggregate> current = citizenRepository.find(citizenId);
        if (current.isEmpty() || current.orElseThrow().lifecycle() != CitizenLifecycle.ACTIVE) {
            return false;
        }
        CitizenAggregate citizen = current.orElseThrow();
        Needs fedNeeds = NeedUpdatePolicy.advance(citizen.needs(), gameTime, needDecayRates)
                .replenish(NeedKind.HUNGER, hungerAmount);
        citizenRepository.update(
                citizen.withSimulation(fedNeeds, citizen.activeTask(), citizen.profession()), citizen.revision());
        recordMemory(citizen.villageId(), new MemoryRecord(
                "fed:" + citizenId + ":" + gameTime, MemoryKind.PLAYER_FED_CITIZEN, MemorySource.OBSERVED,
                citizen.name(), SALIENCE_FED, gameTime, false));
        return true;
    }

    public void selectVillageForLink(UUID player, VillageId villageId, long gameTime) {
        assertServerThread();
        if (acceptingCommands) {
            pendingConsoleLinks.select(player, villageId, gameTime + LINK_EXPIRY_TICKS);
        }
    }

    /** Consumes a player's pending selection and binds a Console if the selected Village still exists. */
    public Optional<VillageId> bindConsole(UUID player, long gameTime) {
        assertServerThread();
        if (!acceptingCommands) {
            return Optional.empty();
        }
        Optional<VillageId> selected = pendingConsoleLinks.consume(player, gameTime);
        if (selected.isEmpty()) {
            return Optional.empty();
        }
        VillageId villageId = selected.orElseThrow();
        return villageRepository.find(villageId).isPresent() ? Optional.of(villageId) : Optional.empty();
    }

    /** Registers an overview session for the viewer and returns the initial snapshot, if available. */
    public Optional<VillageOverviewDto> openOverview(
            UUID viewer, VillageId villageId, String dimensionId, long consolePackedPos, long gameTime) {
        assertServerThread();
        if (!acceptingCommands) {
            return Optional.empty();
        }
        Optional<VillageOverviewDto> overview = overviewQuery.overview(
                villageId, buildLedger(villageId, gameTime).counts(),
                activeProjectStatus(villageId), nextSnapshotRevision());
        if (overview.isEmpty()) {
            return Optional.empty();
        }
        ScreenSessionRegistry.OpenResult opened =
                screenSessions.open(viewer, villageId, dimensionId, consolePackedPos, gameTime);
        if (opened instanceof ScreenSessionRegistry.OpenResult.Rejected) {
            return Optional.empty();
        }
        screenSessions.markServed(viewer, overview.orElseThrow().revision(), gameTime);
        return overview;
    }

    public void closeOverview(UUID viewer) {
        assertServerThread();
        screenSessions.close(viewer);
    }

    private MemoryStore memoriesFor(VillageId villageId) {
        return memoriesByVillage.computeIfAbsent(villageId, villageLogRepository::loadMemories);
    }

    private DecisionLog decisionsFor(VillageId villageId) {
        return decisionsByVillage.computeIfAbsent(villageId, villageLogRepository::loadDecisions);
    }

    /** Remembers an event once (dedupe key) and persists the bounded store. Replay never duplicates. */
    private void recordMemory(VillageId villageId, MemoryRecord record) {
        MemoryStore store = memoriesFor(villageId);
        if (store.add(record)) {
            villageLogRepository.saveMemories(villageId, store.records());
        }
    }

    /** Appends one audited decision and persists the bounded log. */
    private void recordDecision(
            VillageId villageId, DecisionKind kind, String subject, String chosen, String reason, long gameTime) {
        DecisionLog log = decisionsFor(villageId);
        log.record(kind, subject, chosen, DecisionSource.DETERMINISTIC, reason, gameTime);
        villageLogRepository.saveDecisions(villageId, log.all());
    }

    /**
     * One bounded page of the read-only village log for the viewer's open overview session. Memories go
     * out on the first page only; decisions are paginated newest-first by the sequence cursor. The
     * village comes from the server-side session, never from the client.
     */
    public Optional<VillageLogPage> villageLogPage(UUID viewer, long beforeSequence) {
        assertServerThread();
        if (!acceptingCommands) {
            return Optional.empty();
        }
        Optional<VillageId> village = screenSessions.find(Objects.requireNonNull(viewer, "viewer"))
                .map(ScreenSessionRegistry.OverviewSession::villageId);
        if (village.isEmpty()) {
            return Optional.empty();
        }
        VillageId villageId = village.orElseThrow();
        boolean firstPage = beforeSequence == Long.MAX_VALUE;

        List<String> memoryLines = new ArrayList<>();
        if (firstPage) {
            List<MemoryRecord> ranked = memoriesFor(villageId).ranked();
            for (int index = 0; index < ranked.size() && index < MEMORY_PAGE_SIZE; index++) {
                MemoryRecord record = ranked.get(index);
                StringBuilder line = new StringBuilder();
                if (record.pinned()) {
                    line.append("* ");
                }
                line.append(record.kind().name()).append(": ").append(record.subject());
                if (!record.source().isObservedFact()) {
                    line.append(" (").append(record.source().name()).append(')');
                }
                memoryLines.add(line.toString());
            }
        }

        DecisionLog log = decisionsFor(villageId);
        List<DecisionRecord> page = log.page(beforeSequence, DECISION_PAGE_SIZE);
        List<String> decisionLines = new ArrayList<>(page.size());
        long nextCursor = 0;
        for (DecisionRecord record : page) {
            decisionLines.add("#" + record.sequence() + " " + record.subject()
                    + " -> " + record.chosen() + " — " + record.reason());
            nextCursor = record.sequence();
        }
        boolean hasMore = nextCursor > 0 && !log.page(nextCursor, 1).isEmpty();
        return Optional.of(new VillageLogPage(firstPage, memoryLines, decisionLines, nextCursor, hasMore));
    }

    /** Test-facing read of a village's remembered events (ranked). */
    public List<MemoryRecord> villageMemories(VillageId villageId) {
        assertServerThread();
        return memoriesFor(Objects.requireNonNull(villageId, "villageId")).ranked();
    }

    /** Test-facing read of a village's most recent decisions. */
    public List<DecisionRecord> villageDecisions(VillageId villageId, int limit) {
        assertServerThread();
        return decisionsFor(Objects.requireNonNull(villageId, "villageId")).recent(limit);
    }

    /**
     * Proposes a build for the village the viewer currently has open (one project at a time) and returns a
     * fresh overview snapshot so the panel updates immediately. The village comes from the viewer's server
     * session, never from the client, and Java picks the site from the village's own core location.
     */
    public Optional<VillageOverviewDto> proposeBuildForViewer(UUID viewer, long gameTime) {
        assertServerThread();
        if (!acceptingCommands) {
            return Optional.empty();
        }
        Optional<VillageId> village = screenSessions.find(Objects.requireNonNull(viewer, "viewer"))
                .map(ScreenSessionRegistry.OverviewSession::villageId);
        if (village.isEmpty()) {
            return Optional.empty();
        }
        VillageId villageId = village.orElseThrow();
        Optional<VillageAggregate> found = villageRepository.find(villageId);
        if (found.isEmpty() || found.orElseThrow().coreLocation().isEmpty()) {
            return Optional.empty();
        }
        CoreLocation core = found.orElseThrow().coreLocation().orElseThrow();
        ServerLevel level = resolveLevel(core.dimensionId());
        if (level != null && activeProjectStatus(villageId).isEmpty()) {
            proposeProject(level, villageId, BlockPos.of(core.packedPos()), BuildingCatalog.SMALL_STOREHOUSE);
        }
        return overviewQuery.overview(
                villageId, buildLedger(villageId, gameTime).counts(),
                activeProjectStatus(villageId), nextSnapshotRevision());
    }

    /** A short human-readable status of the village's one active project, or empty when there is none. */
    private String activeProjectStatus(VillageId villageId) {
        for (BuildProject project : projectRepository.all()) {
            if (project.villageId().equals(villageId) && !project.state().isTerminal()) {
                String base = project.state().name() + " " + project.placedBlocks() + "/" + project.totalBlocks();
                return project.pauseReason().map(reason -> base + " (" + reason.name() + ")").orElse(base);
            }
        }
        return "";
    }

    /** Monotonic snapshot revision so a pushed overview is never dropped by the client's dedup-by-revision. */
    private long nextSnapshotRevision() {
        if (overviewSnapshotSeq == Long.MAX_VALUE) {
            overviewSnapshotSeq = 0;
        }
        return ++overviewSnapshotSeq;
    }

    public void registerStorage(VillageId villageId, ResourceSource source) {
        assertServerThread();
        Objects.requireNonNull(villageId, "villageId");
        Objects.requireNonNull(source, "source");
        storagesByVillage.computeIfAbsent(villageId, key -> new LinkedHashSet<>()).add(source);
    }

    public void unregisterStorage(VillageId villageId, ResourceSource source) {
        assertServerThread();
        Set<ResourceSource> sources = storagesByVillage.get(Objects.requireNonNull(villageId, "villageId"));
        if (sources != null && sources.remove(source) && sources.isEmpty()) {
            storagesByVillage.remove(villageId);
        }
    }

    /** Aggregates the village's loaded, linked storages into a bounded ledger snapshot. */
    public ResourceLedger buildLedger(VillageId villageId, long gameTime) {
        assertServerThread();
        Map<ResourceKey, Integer> totals = new LinkedHashMap<>();
        Set<ResourceSource> sources = storagesByVillage.get(Objects.requireNonNull(villageId, "villageId"));
        if (sources != null) {
            for (ResourceSource source : sources) {
                source.snapshotCounts().forEach((key, amount) -> totals.merge(key, amount, Integer::sum));
            }
        }
        return new ResourceLedger(totals, gameTime);
    }

    /** Proposes a build project: Java validates a site near the core and admits it, or rejects with a reason. */
    public ProposeResult proposeProject(
            ServerLevel level, VillageId villageId, BlockPos corePos, BuildingTemplateId templateId) {
        assertServerThread();
        if (!acceptingCommands) {
            return new ProposeResult.Rejected(ProposalRejection.NO_SITE);
        }
        WorldPlacementAdapter world = new WorldPlacementAdapter(level);
        ProposeResult result = projectService.propose(
                villageId, level.dimension().location().toString(),
                corePos.getX(), corePos.getY(), corePos.getZ(), templateId, world);
        if (result instanceof ProposeResult.Proposed proposed) {
            projectRepository.create(proposed.project());
            recordDecision(villageId, DecisionKind.PROJECT, templateId.value(),
                    "PROPOSED", "console order", level.getGameTime());
        }
        return result;
    }

    /** Advances one project by a single bounded step (stage materials, then place one block), persisted. */
    public void advanceProject(ProjectId projectId, long gameTime) {
        assertServerThread();
        if (!acceptingCommands) {
            return;
        }
        Optional<BuildProject> found = projectRepository.find(Objects.requireNonNull(projectId, "projectId"));
        if (found.isEmpty() || found.orElseThrow().state().isTerminal()) {
            return;
        }
        BuildProject project = found.orElseThrow();
        ServerLevel level = resolveLevel(project.placement().dimensionId());
        if (level == null) {
            return; // the project's dimension is not loaded; try again later
        }
        WorldPlacementAdapter world = new WorldPlacementAdapter(level);
        VillageId villageId = project.villageId();
        ResourceLedger ledger = buildLedger(villageId, gameTime);
        ResourceWithdrawal withdrawal = (key, amount) -> withdrawFromVillage(villageId, key, amount);
        BuildProject updated = projectService.advance(project, ledger, withdrawal, world);
        if (updated != project) {
            projectRepository.update(updated, project.revision());
            if (updated.isComplete() && !project.isComplete()) {
                recordMemory(villageId, new MemoryRecord(
                        "project:" + projectId, MemoryKind.PROJECT_COMPLETED, MemorySource.OBSERVED,
                        updated.templateId().value(), SALIENCE_PROJECT_COMPLETED, gameTime, false));
                recordDecision(villageId, DecisionKind.PROJECT, updated.templateId().value(),
                        "COMPLETED", "all blocks placed", gameTime);
            }
        }
    }

    public Optional<BuildProject> inspectProject(ProjectId projectId) {
        assertServerThread();
        return projectRepository.find(Objects.requireNonNull(projectId, "projectId"));
    }

    private void advanceActiveProjects(long gameTime) {
        for (BuildProject project : projectRepository.all()) {
            if (!project.state().isTerminal()) {
                advanceProject(project.id(), gameTime);
            }
        }
    }

    private ServerLevel resolveLevel(String dimensionId) {
        ResourceLocation id = ResourceLocation.tryParse(dimensionId);
        if (id == null) {
            return null;
        }
        return server.getLevel(net.minecraft.resources.ResourceKey.create(Registries.DIMENSION, id));
    }

    public void onServerTick(long gameTime) {
        assertServerThread();
        if (acceptingCommands && savedData.isAvailable()) {
            persistenceCoordinator.onServerTick(gameTime);
            if (gameTime % PROJECT_TICK_INTERVAL == 0) {
                advanceActiveProjects(gameTime);
            }
        }
        if (acceptingCommands && llmExecutor != null) {
            // Diagnostic-only in Slice 10: completions surface as status, never as world actions.
            for (LlmResult result : llmExecutor.drainCompleted(AI_DRAIN_PER_TICK)) {
                lastAiDiagnostic = describeAiResult(result);
                SyntValleyLog.logger().info("SyntValley AI diagnostic: {}", lastAiDiagnostic);
            }
        }
    }

    /** Status/metrics text only — the response body itself is size-noted, never logged. */
    private static String describeAiResult(LlmResult result) {
        return switch (result) {
            case LlmResult.Success success -> "ok in " + success.response().durationMillis()
                    + "ms, " + success.response().content().length() + " chars";
            case LlmResult.Failure failure -> "failed " + failure.kind() + " (" + failure.diagnostic() + ")";
        };
    }

    /** One-line health of the LLM boundary for the debug command. */
    public String aiStatus() {
        assertServerThread();
        if (llmExecutor == null) {
            return "backend=" + llmBackendName;
        }
        return "backend=" + llmBackendName
                + " circuit=" + llmExecutor.circuitState()
                + " queued=" + llmExecutor.queuedCount()
                + " completed=" + llmExecutor.completedCount()
                + " dropped=" + llmExecutor.droppedCount()
                + " last=" + lastAiDiagnostic;
    }

    /** Submits a safe diagnostic generation; the reply surfaces via {@link #aiStatus()} once drained. */
    public String aiPing() {
        assertServerThread();
        if (llmExecutor == null || !acceptingCommands) {
            return "backend=" + llmBackendName;
        }
        SubmitResult result = llmExecutor.submit(LlmRequest.of("diagnostic", "Reply with a single word: pong"));
        return switch (result) {
            case SubmitResult.Accepted accepted -> "submitted " + accepted.requestId();
            case SubmitResult.Rejected rejected -> "rejected: " + rejected.reason();
        };
    }

    public void onPostWorldSave(long gameTime) {
        assertServerThread();
        persistenceCoordinator.onPostWorldSave(gameTime);
    }

    public void stop(long gameTime) {
        assertServerThread();
        acceptingCommands = false;
        if (llmExecutor != null) {
            llmExecutor.shutdown(); // late completions are generation-dropped, never enter a stopped server
        }
        if (savedData.isAvailable()) {
            persistenceCoordinator.flushAll(gameTime);
        }
    }

    public int pendingDirtyCount() {
        return persistenceCoordinator.pendingCount();
    }

    private void assertServerThread() {
        if (!server.isSameThread()) {
            throw new IllegalStateException("SyntValley runtime may only be used on the logical server thread");
        }
    }
}
