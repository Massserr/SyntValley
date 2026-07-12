package dev.syntvalley.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.syntvalley.application.port.RepositoryCommitResult;
import dev.syntvalley.application.port.RepositoryRejection;
import dev.syntvalley.application.port.VillageStateRepository;
import dev.syntvalley.domain.identity.VillageId;
import dev.syntvalley.domain.village.CoreLocation;
import dev.syntvalley.domain.village.VillageAggregate;
import dev.syntvalley.domain.village.VillageLifecycle;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CoreBindingServiceTest {
    private static final VillageId ID = new VillageId(UUID.fromString("bc189acb-1928-451b-90d5-b5c86a1cff33"));
    private static final CoreLocation LOCATION = new CoreLocation("minecraft:overworld", 99L);

    @Test
    void replayedPlacementUsesOneVillageAndOneBinding() {
        InMemoryRepository repository = new InMemoryRepository();
        VillageApplicationService villages = new VillageApplicationService(repository, () -> ID);
        CoreBindingService bindings = new CoreBindingService(repository, villages);

        CoreBindingService.EnsureBindingResult.Bound created = assertInstanceOf(
                CoreBindingService.EnsureBindingResult.Bound.class,
                bindings.ensureBound(Optional.empty(), LOCATION, 10)
        );
        assertEquals(CoreBindingService.BindingOutcome.CREATED, created.outcome());
        assertEquals(1, repository.villageCount());

        CoreBindingService.EnsureBindingResult.Bound replayed = assertInstanceOf(
                CoreBindingService.EnsureBindingResult.Bound.class,
                bindings.ensureBound(Optional.of(created.binding()), LOCATION, 11)
        );
        assertEquals(CoreBindingService.BindingOutcome.EXISTING, replayed.outcome());
        assertEquals(created.binding(), replayed.binding());
        assertEquals(1, repository.villageCount());
        assertEquals(1, replayed.village().revision());
    }

    @Test
    void removalOrphansAndSameBindingCanRebindWithNewGeneration() {
        InMemoryRepository repository = new InMemoryRepository();
        CoreBindingService bindings = new CoreBindingService(
                repository,
                new VillageApplicationService(repository, () -> ID)
        );
        CoreBindingService.EnsureBindingResult.Bound created = (CoreBindingService.EnsureBindingResult.Bound)
                bindings.ensureBound(Optional.empty(), LOCATION, 10);

        CoreBindingService.RemoveBindingResult.Orphaned removed = assertInstanceOf(
                CoreBindingService.RemoveBindingResult.Orphaned.class,
                bindings.remove(created.binding(), LOCATION)
        );
        assertEquals(VillageLifecycle.ORPHANED, removed.village().lifecycle());
        assertEquals(2, removed.village().revision());

        CoreBindingService.EnsureBindingResult.Bound rebound = assertInstanceOf(
                CoreBindingService.EnsureBindingResult.Bound.class,
                bindings.ensureBound(Optional.of(created.binding()), LOCATION, 12)
        );
        assertEquals(CoreBindingService.BindingOutcome.REBOUND, rebound.outcome());
        assertEquals(2, rebound.binding().generation());
        assertEquals(3, rebound.village().revision());
        assertEquals(1, repository.villageCount());
    }

    @Test
    void repositoryRejectsWrongExpectedRevision() {
        InMemoryRepository repository = new InMemoryRepository();
        VillageAggregate village = VillageAggregate.create(ID, "Village", LOCATION, 0);
        repository.create(village);
        VillageAggregate orphaned = ((dev.syntvalley.domain.village.VillageTransitionResult.Changed)
                village.orphanCore(village.coreBinding(), LOCATION)).village();

        RepositoryCommitResult.Rejected rejected = assertInstanceOf(
                RepositoryCommitResult.Rejected.class,
                repository.update(orphaned, 99)
        );
        assertEquals(RepositoryRejection.REVISION_CONFLICT, rejected.reason());
        assertEquals(VillageLifecycle.ACTIVE, repository.find(ID).orElseThrow().lifecycle());
    }

    private static final class InMemoryRepository implements VillageStateRepository {
        private final Map<VillageId, VillageAggregate> villages = new LinkedHashMap<>();

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public Optional<VillageAggregate> find(VillageId villageId) {
            return Optional.ofNullable(villages.get(villageId));
        }

        @Override
        public int villageCount() {
            return villages.size();
        }

        @Override
        public RepositoryCommitResult create(VillageAggregate village) {
            if (villages.putIfAbsent(village.id(), village) != null) {
                return new RepositoryCommitResult.Rejected(RepositoryRejection.DUPLICATE_ID);
            }
            return new RepositoryCommitResult.Committed(village);
        }

        @Override
        public RepositoryCommitResult update(VillageAggregate village, long expectedRevision) {
            VillageAggregate current = villages.get(village.id());
            if (current == null) {
                return new RepositoryCommitResult.Rejected(RepositoryRejection.MISSING_RECORD);
            }
            if (current.revision() != expectedRevision || village.revision() != expectedRevision + 1) {
                return new RepositoryCommitResult.Rejected(
                        RepositoryRejection.REVISION_CONFLICT,
                        Optional.of(current)
                );
            }
            villages.put(village.id(), village);
            return new RepositoryCommitResult.Committed(village);
        }
    }
}
