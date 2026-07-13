package dev.syntvalley.application.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.syntvalley.domain.citizen.CitizenAggregate;
import dev.syntvalley.domain.identity.CitizenId;
import dev.syntvalley.domain.identity.TaskId;
import dev.syntvalley.domain.identity.VillageId;
import dev.syntvalley.domain.need.NeedDecayRates;
import dev.syntvalley.domain.need.Needs;
import dev.syntvalley.domain.task.Task;
import dev.syntvalley.domain.task.TaskKind;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CitizenSimulationStepTest {
    private static final CitizenId CITIZEN = new CitizenId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final VillageId VILLAGE = new VillageId(UUID.fromString("22222222-2222-2222-2222-222222222222"));

    private final CitizenSimulationStep step = new CitizenSimulationStep(new NeedDecayRates(100, 5, 3), 40);

    private static CitizenAggregate freshCitizen() {
        return CitizenAggregate.create(CITIZEN, VILLAGE, "Settler", 0);
    }

    @Test
    void calmCitizenDecaysAndIdles() {
        CitizenAggregate advanced = step.advance(freshCitizen(), 1000, TaskId::random);

        assertEquals(950, advanced.needs().hunger());
        assertEquals(970, advanced.needs().rest());
        assertEquals(TaskKind.IDLE, advanced.activeTask().orElseThrow().kind());
    }

    @Test
    void criticalHungerRequestsFood() {
        CitizenAggregate advanced = step.advance(freshCitizen(), 16_000, TaskId::random);

        assertEquals(200, advanced.needs().hunger());
        assertEquals(520, advanced.needs().rest());
        assertEquals(TaskKind.REQUEST_FOOD, advanced.activeTask().orElseThrow().kind());
    }

    @Test
    void restingRecoversRest() {
        CitizenAggregate tired = freshCitizen().withSimulation(
                new Needs(800, 150, 0),
                Optional.of(Task.create(TaskId.random(), CITIZEN, TaskKind.REST, 0)));

        CitizenAggregate rested = step.advance(tired, 100, TaskId::random);

        assertEquals(187, rested.needs().rest(), "decays 3 then recovers 40 while resting");
        assertEquals(TaskKind.REST, rested.activeTask().orElseThrow().kind(), "keeps resting while still critical");
        assertTrue(rested.needs().rest() > tired.needs().rest());
    }

    @Test
    void repeatedStepWithoutElapsedTimeIsANoOp() {
        CitizenAggregate advanced = step.advance(freshCitizen(), 16_000, TaskId::random);
        assertSame(advanced, step.advance(advanced, 16_000, TaskId::random), "no elapsed time and same plan is a no-op");
    }
}
