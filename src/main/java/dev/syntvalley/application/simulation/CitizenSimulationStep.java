package dev.syntvalley.application.simulation;

import dev.syntvalley.application.scheduler.TaskDecision;
import dev.syntvalley.application.scheduler.TaskPlanner;
import dev.syntvalley.domain.citizen.CitizenAggregate;
import dev.syntvalley.domain.identity.TaskId;
import dev.syntvalley.domain.need.NeedDecayRates;
import dev.syntvalley.domain.need.NeedKind;
import dev.syntvalley.domain.need.NeedUpdatePolicy;
import dev.syntvalley.domain.need.Needs;
import dev.syntvalley.domain.task.Task;
import dev.syntvalley.domain.task.TaskKind;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The Minecraft-free "brain" of the Slice 5 loop: advance a citizen's needs by elapsed game time,
 * apply the active task's effect (Slice 5 rests in place, so a REST task recovers rest), then choose
 * the active task deterministically. Task execution beyond this rest-in-place effect (navigation,
 * social rest, named-place rest) lands in later slices. Returns the same instance when nothing
 * changed so no revision or write is spent.
 */
public final class CitizenSimulationStep {
    private final TaskPlanner planner = new TaskPlanner();
    private final NeedDecayRates decayRates;
    private final int restRecoveryPerStep;

    public CitizenSimulationStep(NeedDecayRates decayRates, int restRecoveryPerStep) {
        this.decayRates = Objects.requireNonNull(decayRates, "decayRates");
        if (restRecoveryPerStep < 0) {
            throw new IllegalArgumentException("restRecoveryPerStep must not be negative");
        }
        this.restRecoveryPerStep = restRecoveryPerStep;
    }

    public CitizenAggregate advance(CitizenAggregate citizen, long now, Supplier<TaskId> taskIdFactory) {
        Objects.requireNonNull(citizen, "citizen");
        Objects.requireNonNull(taskIdFactory, "taskIdFactory");

        Needs decayed = NeedUpdatePolicy.advance(citizen.needs(), now, decayRates);

        Optional<TaskKind> runningKind = citizen.activeTask()
                .filter(task -> !task.state().isTerminal())
                .map(Task::kind);
        Needs afterEffect = runningKind.filter(kind -> kind == TaskKind.REST).isPresent()
                ? decayed.replenish(NeedKind.REST, restRecoveryPerStep)
                : decayed;

        TaskDecision decision = planner.plan(runningKind, afterEffect);
        Optional<Task> nextActiveTask = switch (decision) {
            case TaskDecision.Keep ignored -> citizen.activeTask();
            case TaskDecision.Start start ->
                    Optional.of(Task.create(taskIdFactory.get(), citizen.id(), start.kind(), now));
            case TaskDecision.Preempt preempt ->
                    Optional.of(Task.create(taskIdFactory.get(), citizen.id(), preempt.kind(), now));
        };

        if (afterEffect.equals(citizen.needs()) && nextActiveTask.equals(citizen.activeTask())) {
            return citizen;
        }
        return citizen.withSimulation(afterEffect, nextActiveTask);
    }
}
