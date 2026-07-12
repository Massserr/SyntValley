package dev.syntvalley.domain.village;

public enum VillageTransitionRejection {
    BINDING_CONFLICT,
    STALE_BINDING,
    LIFECYCLE_DISALLOWS_BINDING,
    GENERATION_EXHAUSTED,
    REVISION_EXHAUSTED
}
