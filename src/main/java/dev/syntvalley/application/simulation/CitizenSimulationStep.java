package dev.syntvalley.application.simulation;

import dev.syntvalley.application.scheduler.TaskDecision;
import dev.syntvalley.application.scheduler.TaskPlanner;
import dev.syntvalley.domain.citizen.CitizenAggregate;
import dev.syntvalley.domain.identity.TaskId;
import dev.syntvalley.domain.need.NeedDecayRates;
import dev.syntvalley.domain.need.NeedKind;
import dev.syntvalley.domain.need.NeedUpdatePolicy;
import dev.syntvalley.domain.need.Needs;
import dev.syntvalley.domain.profession.CitizenProfession;
import dev.syntvalley.domain.profession.ProfessionDefinition;
import dev.syntvalley.domain.task.Task;
import dev.syntvalley.domain.task.TaskKind;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The Minecraft-free "brain": advance needs, apply the active task's effect (REST recovers in place),
 * then choose the active task. A calm citizen with a work-capable profession runs bounded WORK shifts
 * that grant experience once each and alternate with an idle cooldown; a critical need always
 * preempts work. The profession definition is supplied by the caller so this stays pure.
 */
public final class CitizenSimulationStep {
    private static final int WORK_SHIFT_TICKS = 200;
    private static final int WORK_COOLDOWN_TICKS = 100;
    private static final int WORK_EXPERIENCE_PER_SHIFT = 25;

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

    public CitizenAggregate advance(
            CitizenAggregate citizen,
            long now,
            Supplier<TaskId> taskIdFactory,
            Optional<ProfessionDefinition> definition) {
        Objects.requireNonNull(citizen, "citizen");
        Objects.requireNonNull(taskIdFactory, "taskIdFactory");
        Objects.requireNonNull(definition, "definition");

        Needs decayed = NeedUpdatePolicy.advance(citizen.needs(), now, decayRates);
        Optional<Task> current = citizen.activeTask().filter(task -> !task.state().isTerminal());
        Optional<TaskKind> runningKind = current.map(Task::kind);
        Needs afterEffect = runningKind.filter(kind -> kind == TaskKind.REST).isPresent()
                ? decayed.replenish(NeedKind.REST, restRecoveryPerStep)
                : decayed;

        boolean canWork = planner.desired(afterEffect) == TaskKind.IDLE
                && citizen.profession().isPresent()
                && definition.map(def -> def.allows(TaskKind.WORK)).orElse(false);

        Optional<Task> nextTask;
        Optional<CitizenProfession> nextProfession = citizen.profession();

        if (canWork) {
            ProfessionDefinition def = definition.orElseThrow();
            if (runningKind.filter(kind -> kind == TaskKind.WORK).isPresent()) {
                Task work = current.orElseThrow();
                if (now - work.createdGameTime() >= WORK_SHIFT_TICKS) {
                    nextProfession = citizen.profession().map(p -> p.gainExperience(WORK_EXPERIENCE_PER_SHIFT, def));
                    nextTask = Optional.of(Task.create(taskIdFactory.get(), citizen.id(), TaskKind.IDLE, now));
                } else {
                    nextTask = current;
                }
            } else if (runningKind.filter(kind -> kind == TaskKind.IDLE).isPresent()
                    && now - current.orElseThrow().createdGameTime() < WORK_COOLDOWN_TICKS) {
                nextTask = current;
            } else {
                nextTask = Optional.of(Task.create(taskIdFactory.get(), citizen.id(), TaskKind.WORK, now));
            }
        } else {
            TaskDecision decision = planner.plan(runningKind, afterEffect);
            nextTask = switch (decision) {
                case TaskDecision.Keep ignored -> citizen.activeTask();
                case TaskDecision.Start start ->
                        Optional.of(Task.create(taskIdFactory.get(), citizen.id(), start.kind(), now));
                case TaskDecision.Preempt preempt ->
                        Optional.of(Task.create(taskIdFactory.get(), citizen.id(), preempt.kind(), now));
            };
        }

        if (afterEffect.equals(citizen.needs())
                && nextTask.equals(citizen.activeTask())
                && nextProfession.equals(citizen.profession())) {
            return citizen;
        }
        return citizen.withSimulation(afterEffect, nextTask, nextProfession);
    }
}
