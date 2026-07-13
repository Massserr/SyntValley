package dev.syntvalley.application.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.syntvalley.domain.need.Needs;
import dev.syntvalley.domain.task.TaskKind;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TaskPlannerTest {
    private final TaskPlanner planner = new TaskPlanner();

    private static final Needs CALM = new Needs(800, 800, 0);
    private static final Needs HUNGRY = new Needs(150, 800, 0);
    private static final Needs TIRED = new Needs(800, 150, 0);
    private static final Needs HUNGRY_AND_TIRED = new Needs(100, 150, 0);

    @Test
    void startsDesiredWhenThereIsNoTask() {
        assertEquals(TaskKind.IDLE,
                assertInstanceOf(TaskDecision.Start.class, planner.plan(Optional.empty(), CALM)).kind());
        assertEquals(TaskKind.REQUEST_FOOD,
                assertInstanceOf(TaskDecision.Start.class, planner.plan(Optional.empty(), HUNGRY)).kind());
        assertEquals(TaskKind.REST,
                assertInstanceOf(TaskDecision.Start.class, planner.plan(Optional.empty(), TIRED)).kind());
    }

    @Test
    void criticalNeedPreemptsIdle() {
        assertEquals(TaskKind.REQUEST_FOOD,
                assertInstanceOf(TaskDecision.Preempt.class, planner.plan(Optional.of(TaskKind.IDLE), HUNGRY)).kind());
    }

    @Test
    void keepsMatchingTaskAndLetsCriticalTaskFinish() {
        assertInstanceOf(TaskDecision.Keep.class, planner.plan(Optional.of(TaskKind.REST), TIRED));
        assertInstanceOf(TaskDecision.Keep.class, planner.plan(Optional.of(TaskKind.IDLE), CALM));
        assertInstanceOf(TaskDecision.Keep.class, planner.plan(Optional.of(TaskKind.REST), CALM));
    }

    @Test
    void moreSevereCriticalPreemptsLessSevere() {
        assertEquals(TaskKind.REQUEST_FOOD,
                assertInstanceOf(TaskDecision.Preempt.class,
                        planner.plan(Optional.of(TaskKind.REST), HUNGRY_AND_TIRED)).kind());
    }
}
