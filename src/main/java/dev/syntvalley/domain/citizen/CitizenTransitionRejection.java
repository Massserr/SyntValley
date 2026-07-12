package dev.syntvalley.domain.citizen;

public enum CitizenTransitionRejection {
    BINDING_CONFLICT,
    STALE_BINDING,
    LIFECYCLE_DISALLOWS_PRESENCE,
    DUPLICATE_ENTITY,
    REVISION_EXHAUSTED
}
