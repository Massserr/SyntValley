package dev.syntvalley.domain.project;

/**
 * Build project lifecycle. STAGING gathers the bill of materials; BUILDING places blocks stage by stage;
 * PAUSED is a recoverable stop with a reason (obstruction, protection, unloaded chunk); COMPLETED and
 * CANCELLED are terminal. There is no automatic destructive rollback.
 */
public enum ProjectState {
    STAGING,
    BUILDING,
    PAUSED,
    COMPLETED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }
}
