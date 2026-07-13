package dev.syntvalley.application.simulation;

/** Upper bound on citizens serviced by the simulation per server tick. */
public record TickBudget(int maxCitizensPerTick) {
    public TickBudget {
        if (maxCitizensPerTick < 1) {
            throw new IllegalArgumentException("maxCitizensPerTick must be positive");
        }
    }

    public static TickBudget defaults() {
        return new TickBudget(8);
    }
}
