package dev.syntvalley.domain.memory;

/** The kinds of events the village remembers. Deterministic, LLM-free; expanded as slices add events. */
public enum MemoryKind {
    PROJECT_COMPLETED,
    PLAYER_FED_CITIZEN,
    PLAYER_HELPED,
    CITIZEN_DIED
}
