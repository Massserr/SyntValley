package dev.syntvalley.domain.task;

public enum TaskTransitionRejection {
    NOT_PENDING,
    NOT_RUNNING,
    BACKOFF_ACTIVE,
    ALREADY_TERMINAL
}
