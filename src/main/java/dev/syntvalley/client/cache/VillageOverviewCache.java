package dev.syntvalley.client.cache;

import dev.syntvalley.application.query.VillageOverviewDto;
import java.util.Objects;
import java.util.Optional;

/**
 * Client-side presentation state for the Village overview. Pure logic (no Minecraft types) so the
 * revision/loading/stale/error transitions are unit-testable; the screen only renders this state.
 */
public final class VillageOverviewCache {
    public enum State {
        LOADING,
        LOADED,
        STALE,
        ERROR,
        CLOSED
    }

    private State state = State.CLOSED;
    private VillageOverviewDto current;
    private long revision;

    public State state() {
        return state;
    }

    public Optional<VillageOverviewDto> current() {
        return Optional.ofNullable(current);
    }

    public long revision() {
        return revision;
    }

    /** Begins a fresh load, discarding any previously cached snapshot. */
    public void requestOpen() {
        state = State.LOADING;
        current = null;
        revision = 0;
    }

    /**
     * Accepts a server snapshot. Older-or-equal revisions are ignored so a delayed packet cannot
     * overwrite newer state. Returns whether the snapshot was applied.
     */
    public boolean accept(VillageOverviewDto dto) {
        Objects.requireNonNull(dto, "dto");
        if (state == State.CLOSED) {
            return false;
        }
        if (current != null && dto.revision() <= revision) {
            return false;
        }
        current = dto;
        revision = dto.revision();
        state = State.LOADED;
        return true;
    }

    /** Flags the shown data as possibly outdated without discarding it. */
    public void markStale() {
        if (state == State.LOADED) {
            state = State.STALE;
        }
    }

    public void fail() {
        if (state != State.CLOSED) {
            state = State.ERROR;
        }
    }

    public void close() {
        state = State.CLOSED;
        current = null;
        revision = 0;
    }
}
