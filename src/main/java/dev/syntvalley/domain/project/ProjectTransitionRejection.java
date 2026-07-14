package dev.syntvalley.domain.project;

/** Why a build project state transition was refused. */
public enum ProjectTransitionRejection {
    NOT_STAGING,
    NOT_BUILDING,
    NOT_PAUSED,
    ALREADY_TERMINAL,
    REVISION_EXHAUSTED
}
