package dev.syntvalley.domain.project;

/** Why a build project is paused. Stable codes so the overview and logs can explain the stop. */
public enum ProjectPauseReason {
    SITE_OBSTRUCTED,
    SITE_PROTECTED,
    CHUNK_UNLOADED,
    MISSING_MATERIALS,
    TEMPLATE_VERSION_MISMATCH
}
