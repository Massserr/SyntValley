package dev.syntvalley.application.simulation;

import dev.syntvalley.application.scheduler.TaskDecision;
import dev.syntvalley.application.scheduler.TaskPlanner;
import dev.syntvalley.domain.citizen.CitizenAggregate;
import dev.syntvalley.domain.identity.TaskId;
import dev.syntvalley.domain.need.NeedDecayRates;
import dev.syntvalley.domain.need.NeedUpdatePolicy;
import dev.syntvalley.domain.need.Needs;
import dev.syntvalley.domain.task.Task;
import dev.syntvalley.domain.task.TaskKind;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The Minecraft-free "brain" of the Slice 5 loop: advance a citizen's needs by elapsed game time and
 * choose the active task deterministically. Task execution (navigation, lease, success/failure) lives
 * in the server adapter; this only decides what the citizen should be doing. If nothing changed the
 * same instance is returned so no revision or write is spent.
 */
public final class CitizenSimulationStep {
    private final TaskPlanner planner = new TaskPlanner();
    private final NeedDecayRates decayRates;

    public CitizenSimulationStep(NeedDecayRates decayRates) {
        this.decayRates = Objects.requireNonNull(decayRates, "decayRates");
    }

    public CitizenAggregate advance(CitizenAggregate citizen, long now, Supplier<TaskId> taskIdFactory) {
        Objects.requireNonNull(citizen, "citizen");
        Objects.requireNonNull(taskIdFactory, "taskIdFactory");

        Needs updatedNeeds = NeedUpdatePolicy.advance(citizen.needs(), now, decayRates);

        Optional<TaskKind> runningKind = citizen.activeTask()
                .filter(task -> !task.state().isTerminal())
                .map(Task::kind);
        TaskDecision decision = planner.plan(runningKind, updatedNeeds);

        Optional<Task> nextActiveTask = switch (decision) {
            case TaskDecision.Keep ignored -> citizen.activeTask();
            case TaskDecision.Start start ->
                    Optional.of(Task.create(taskIdFactory.get(), citizen.id(), start.kind(), now));
            case TaskDecision.Preempt preempt ->
                    Optional.of(Task.create(taskIdFactory.get(), citizen.id(), preempt.kind(), now));
        };

        if (updatedNeeds.equals(citizen.needs()) && nextActiveTask.equals(citizen.activeTask())) {
            return citizen;
        }
        return citizen.withSimulation(updatedNeeds, nextActiveTask);
    }
}
