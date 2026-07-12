package dev.syntvalley.bootstrap;

import dev.syntvalley.application.port.VillageStateRepository;
import dev.syntvalley.application.service.CoreBindingService;
import dev.syntvalley.application.service.VillageApplicationService;
import dev.syntvalley.domain.identity.VillageId;
import dev.syntvalley.domain.village.CoreBinding;
import dev.syntvalley.domain.village.CoreLocation;
import dev.syntvalley.domain.village.VillageAggregate;
import dev.syntvalley.persistence.dirty.DirtyTracker;
import dev.syntvalley.persistence.dirty.PersistenceCoordinator;
import dev.syntvalley.persistence.saveddata.PersistenceBounds;
import dev.syntvalley.persistence.saveddata.SavedDataVillageRepository;
import dev.syntvalley.persistence.saveddata.SyntValleySavedData;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;

/** Per-MinecraftServer composition root. Canonical gameplay state remains in SavedData. */
public final class SyntValleyServerRuntime {
    private final MinecraftServer server;
    private final SyntValleySavedData savedData;
    private final VillageStateRepository villageRepository;
    private final CoreBindingService coreBindingService;
    private final PersistenceCoordinator persistenceCoordinator;
    private boolean acceptingCommands = true;

    public SyntValleyServerRuntime(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
        this.savedData = SyntValleySavedData.get(server);
        DirtyTracker dirtyTracker = new DirtyTracker(PersistenceBounds.MAX_VILLAGES, savedData::setDirty);
        this.villageRepository = new SavedDataVillageRepository(server, savedData, dirtyTracker);
        VillageApplicationService villageService = new VillageApplicationService(villageRepository, VillageId::random);
        this.coreBindingService = new CoreBindingService(villageRepository, villageService);
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
