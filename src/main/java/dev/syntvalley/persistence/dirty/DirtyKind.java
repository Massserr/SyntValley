package dev.syntvalley.persistence.dirty;

public enum DirtyKind {
    VILLAGE,
    CITIZEN,
    PROJECT,
    /** A village's whole memory store (keyed by the village UUID). */
    MEMORY,
    /** A village's whole decision log (keyed by the village UUID). */
    DECISION
}
