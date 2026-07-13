package dev.syntvalley.bootstrap;

import dev.syntvalley.application.port.CitizenStateRepository;
import dev.syntvalley.application.port.VillageStateRepository;
import dev.syntvalley.application.profession.ProfessionCatalog;
import dev.syntvalley.application.query.VillageOverviewDto;
import dev.syntvalley.application.query.VillageOverviewQuery;
import dev.syntvalley.application.service.CitizenApplicationService;
import dev.syntvalley.application.service.CitizenBindingService;
import dev.syntvalley.application.service.CoreBindingService;
import dev.syntvalley.application.service.PendingConsoleLinks;
import dev.syntvalley.application.service.ScreenSessionRegistry;
import dev.syntvalley.application.service.VillageApplicationService;
import dev.syntvalley.application.simulation.CitizenSimulationStep;
import dev.syntvalley.domain.citizen.CitizenAggregate;
import dev.syntvalley.domain.citizen.CitizenEntityBinding;
import dev.syntvalley.domain.citizen.CitizenLifecycle;
import dev.syntvalley.domain.identity.CitizenId;
import dev.syntvalley.domain.identity.TaskId;
import dev.syntvalley.domain.identity.VillageId;
import dev.syntvalley.domain.need.NeedDecayRates;
import dev.syntvalley.domain.need.NeedKind;
import dev.syntvalley.domain.need.NeedUpdatePolicy;
import dev.syntvalley.domain.need.Needs;
import dev.syntvalley.domain.profession.CitizenProfession;
import dev.syntvalley.domain.profession.ProfessionDefinition;
import dev.syntvalley.domain.profession.ProfessionId;
import dev.syntvalley.domain.village.CoreBinding;
import dev.syntvalley.domain.village.CoreLocation;
import dev.syntvalley.domain.village.VillageAggregate;
import dev.syntvalley.persistence.dirty.DirtyTracker;
import dev.syntvalley.persistence.dirty.PersistenceCoordinator;
import dev.syntvalley.persistence.saveddata.PersistenceBounds;
import dev.syntvalley.persistence.saveddata.SavedDataCitizenRepository;
import dev.syntvalley.persistence.saveddata.SavedDataVillageRepository;
import dev.syntvalley.persistence.saveddata.SyntValleySavedData;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/** Per-MinecraftServer composition root. Canonical gameplay state remains in SavedData. */
public final class SyntValleyServerRuntime {
    /** How long a "select Village for Console link" selection stays valid, in game ticks. */
    public static final long LINK_EXPIRY_TICKS = 600L;

    /** Rest points recovered per serviced simulation step while a citizen rests in place. */
    private static final int REST_RECOVERY_PER_STEP = 60;

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
    private final PersistenceCoordinator persistenceCoordinator;
    private boolean acceptingCommands = true;

    public SyntValleyServerRuntime(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
        this.savedData = SyntValleySavedData.get(server);
        DirtyTracker dirtyTracker = new DirtyTracker(PersistenceBounds.MAX_DIRTY_KEYS, savedData::setDirty);
        this.villageRepository = new SavedDataVillageRepository(server, savedData, dirtyTracker);
        VillageApplicationService villageService = new VillageApplicationService(villageRepository, VillageId::random);
        this.coreBindingService = new CoreBindingService(villageRepository, villageService);
        this.citizenRepository = new SavedDataCitizenRepository(server, savedData, dirtyTracker);
        this.citizenService = new CitizenApplicationService(citizenRepository, villageRepository, CitizenId::random);
        this.citizenBindingService = new CitizenBindingService(citizenRepository);
        this.overviewQuery = new VillageOverviewQuery(villageRepository, citizenRepository);
        this.persistenceCoordinator = new PersistenceCoordinator(savedData, dirtyTracker);
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
        return citizenBindingService.recordDeath(binding, entityId);
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
        CitizenAggregate advanced = simulationStep.advance(citizen, gameTime, TaskId::random, definition);
        if (advanced != citizen) {
            citizenRepository.update(advanced, citizen.revision());
        }
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
        Optional<VillageOverviewDto> overview = overviewQuery.overview(villageId);
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

    public void onServerTick(long gameTime) {
        assertServerThread();
        if (acceptingCommands && savedData.isAvailable()) {
            persistenceCoordinator.onServerTick(gameTime);
        }
    }

    public void onPostWorldSave(long gameTime) {
        assertServerThread();
        persistenceCoordinator.onPostWorldSave(gameTime);
    }

    public void stop(long gameTime) {
        assertServerThread();
        acceptingCommands = false;
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
