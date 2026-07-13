package dev.syntvalley.domain.task;

public enum TaskFailureReason {
    NO_PATH,
    TARGET_UNLOADED,
    TIMEOUT,
    OBSTRUCTED,
    SUPERSEDED,
    /** The ledger promised a resource that the physical inventory no longer held at execution time. */
    STALE_RESOURCE_VIEW
}
