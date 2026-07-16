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
import dev.syntvalley.domain.personality.Personality;
import dev.syntvalley.domain.profession.CitizenProfession;
import dev.syntvalley.domain.profession.ProfessionDefinition;
import dev.syntvalley.domain.profession.ProfessionId;
import dev.syntvalley.domain.task.Task;
import dev.syntvalley.domain.task.TaskKind;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CitizenSimulationStepTest {
    private static final CitizenId CITIZEN = new CitizenId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final VillageId VILLAGE = new VillageId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
    private static final ProfessionId CARETAKER = new ProfessionId("syntvalley:caretaker");
    private static final ProfessionDefinition CARETAKER_DEFINITION =
            new ProfessionDefinition(CARETAKER, 1, Set.of(TaskKind.WORK), 1000, 5);
    private static final Personality BALANCED = Personality.defaults();

    private final CitizenSimulationStep step = new CitizenSimulationStep(new NeedDecayRates(100, 5, 3), 40);

    private static CitizenAggregate freshCitizen() {
        return CitizenAggregate.create(CITIZEN, VILLAGE, "Settler", 0);
    }

    private static CitizenAggregate calmWorker(int hunger, int rest) {
        return freshCitizen().withSimulation(
                new Needs(hunger, rest, 0), Optional.empty(),
                Optional.of(CitizenProfession.assign(CARETAKER, 0)));
    }

    @Test
    void calmCitizenDecaysAndIdles() {
        CitizenAggregate advanced = step.advance(freshCitizen(), 1000, TaskId::random, Optional.empty(), BALANCED);

        assertEquals(950, advanced.needs().hunger());
        assertEquals(970, advanced.needs().rest());
        assertEquals(TaskKind.IDLE, advanced.activeTask().orElseThrow().kind());
    }

    @Test
    void criticalHungerRequestsFood() {
        CitizenAggregate advanced = step.advance(freshCitizen(), 16_000, TaskId::random, Optional.empty(), BALANCED);

        assertEquals(200, advanced.needs().hunger());
        assertEquals(520, advanced.needs().rest());
        assertEquals(TaskKind.REQUEST_FOOD, advanced.activeTask().orElseThrow().kind());
    }

    @Test
    void restingRecoversRest() {
        CitizenAggregate tired = freshCitizen().withSimulation(
                new Needs(800, 150, 0),
                Optional.of(Task.create(TaskId.random(), CITIZEN, TaskKind.REST, 0)),
                Optional.empty());

        CitizenAggregate rested = step.advance(tired, 100, TaskId::random, Optional.empty(), BALANCED);

        assertEquals(187, rested.needs().rest(), "decays 3 then recovers 40 while resting");
        assertEquals(TaskKind.REST, rested.activeTask().orElseThrow().kind(), "keeps resting while still critical");
        assertTrue(rested.needs().rest() > tired.needs().rest());
    }

    @Test
    void workingCitizenRunsShiftsAndGainsExperience() {
        CitizenAggregate worker = freshCitizen()
                .withProfession(Optional.of(CitizenProfession.assign(CARETAKER, 0)));

        CitizenAggregate working =
                step.advance(worker, 100, TaskId::random, Optional.of(CARETAKER_DEFINITION), BALANCED);
        assertEquals(TaskKind.WORK, working.activeTask().orElseThrow().kind(), "a calm caretaker starts working");

        CitizenAggregate afterShift =
                step.advance(working, 100 + 200, TaskId::random, Optional.of(CARETAKER_DEFINITION), BALANCED);
        assertEquals(TaskKind.IDLE, afterShift.activeTask().orElseThrow().kind(), "a finished shift rests before the next");
        assertTrue(afterShift.profession().orElseThrow().experience() > 0, "the shift granted experience");
    }

    @Test
    void diligentCalmCitizenTakesAShift() {
        CitizenAggregate advanced = step.advance(calmWorker(500, 500), 1, TaskId::random,
                Optional.of(CARETAKER_DEFINITION), new Personality(90, 50));
        assertEquals(TaskKind.WORK, advanced.activeTask().orElseThrow().kind(),
                "a diligent citizen chooses to work when calm");
    }

    @Test
    void lazyCalmCitizenIdlesInsteadOfWorking() {
        CitizenAggregate advanced = step.advance(calmWorker(500, 500), 1, TaskId::random,
                Optional.of(CARETAKER_DEFINITION), new Personality(0, 50));
        assertEquals(TaskKind.IDLE, advanced.activeTask().orElseThrow().kind(),
                "a less diligent citizen idles instead of taking a shift");
    }

    @Test
    void personalityNeverOverridesACriticalNeed() {
        CitizenAggregate advanced = step.advance(calmWorker(150, 500), 1, TaskId::random,
                Optional.of(CARETAKER_DEFINITION), new Personality(0, 50));
        assertEquals(TaskKind.REQUEST_FOOD, advanced.activeTask().orElseThrow().kind(),
                "a critical need is chosen regardless of a lazy personality");
    }

    @Test
    void repeatedStepWithoutElapsedTimeIsANoOp() {
        CitizenAggregate advanced = step.advance(freshCitizen(), 16_000, TaskId::random, Optional.empty(), BALANCED);
        assertSame(advanced, step.advance(advanced, 16_000, TaskId::random, Optional.empty(), BALANCED),
                "no elapsed time and same plan is a no-op");
    }
}
