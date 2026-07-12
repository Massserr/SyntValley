package dev.syntvalley.application.port;

public enum RepositoryRejection {
    DUPLICATE_ID,
    MISSING_RECORD,
    REVISION_CONFLICT,
    CAPACITY_REACHED,
    DATA_REVISION_EXHAUSTED,
    PERSISTENCE_UNAVAILABLE
}
