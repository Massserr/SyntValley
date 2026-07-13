package dev.syntvalley.application.service;

import dev.syntvalley.domain.identity.VillageId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-authoritative registry of open Village overview sessions. Pure bookkeeping so it stays
 * unit-testable: owner, bound target, rate gate and expiry only; networking lives in the MC adapter.
 */
public final class ScreenSessionRegistry {
    public static final int MAX_SESSIONS = 256;

    private final int maxSessions;
    private final Map<UUID, OverviewSession> sessions = new LinkedHashMap<>();

    public ScreenSessionRegistry() {
        this(MAX_SESSIONS);
    }

    public ScreenSessionRegistry(int maxSessions) {
        if (maxSessions < 1) {
            throw new IllegalArgumentException("maxSessions must be positive");
        }
        this.maxSessions = maxSessions;
    }

    public OpenResult open(UUID viewer, VillageId villageId, String dimensionId, long consolePackedPos, long gameTime) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(villageId, "villageId");
        Objects.requireNonNull(dimensionId, "dimensionId");
        if (gameTime < 0) {
            throw new IllegalArgumentException("gameTime must not be negative");
        }
        if (!sessions.containsKey(viewer) && sessions.size() >= maxSessions) {
            return new OpenResult.Rejected();
        }
        OverviewSession session =
                new OverviewSession(viewer, villageId, dimensionId, consolePackedPos, gameTime, gameTime, 0L);
        sessions.put(viewer, session);
        return new OpenResult.Opened(session);
    }

    public Optional<OverviewSession> find(UUID viewer) {
        return Optional.ofNullable(sessions.get(Objects.requireNonNull(viewer, "viewer")));
    }

    public boolean close(UUID viewer) {
        return sessions.remove(Objects.requireNonNull(viewer, "viewer")) != null;
    }

    /** True only if the viewer has a session bound to exactly this Console target. */
    public boolean matches(UUID viewer, VillageId villageId, String dimensionId, long consolePackedPos) {
        OverviewSession session = sessions.get(Objects.requireNonNull(viewer, "viewer"));
        return session != null
                && session.villageId().equals(villageId)
                && session.dimensionId().equals(dimensionId)
                && session.consolePackedPos() == consolePackedPos;
    }

    /** Rate gate: whether the viewer may be served again after a minimum interval since last activity. */
    public boolean canServe(UUID viewer, long gameTime, long minIntervalTicks) {
        OverviewSession session = sessions.get(Objects.requireNonNull(viewer, "viewer"));
        return session != null && gameTime - session.lastActivityGameTime() >= minIntervalTicks;
    }

    public void markServed(UUID viewer, long revision, long gameTime) {
        OverviewSession session = sessions.get(Objects.requireNonNull(viewer, "viewer"));
        if (session != null) {
            sessions.put(viewer, session.served(revision, gameTime));
        }
    }

    /** Removes sessions idle longer than maxIdleTicks and returns the removed viewers. */
    public List<UUID> expire(long gameTime, long maxIdleTicks) {
        List<UUID> removed = new ArrayList<>();
        sessions.entrySet().removeIf(entry -> {
            boolean stale = gameTime - entry.getValue().lastActivityGameTime() > maxIdleTicks;
            if (stale) {
                removed.add(entry.getKey());
            }
            return stale;
        });
        return List.copyOf(removed);
    }

    public int size() {
        return sessions.size();
    }

    public record OverviewSession(
            UUID viewer,
            VillageId villageId,
            String dimensionId,
            long consolePackedPos,
            long openedGameTime,
            long lastActivityGameTime,
            long lastServedRevision
    ) {
        public OverviewSession {
            Objects.requireNonNull(viewer, "viewer");
            Objects.requireNonNull(villageId, "villageId");
            Objects.requireNonNull(dimensionId, "dimensionId");
        }

        OverviewSession served(long revision, long gameTime) {
            return new OverviewSession(
                    viewer, villageId, dimensionId, consolePackedPos, openedGameTime, gameTime, revision);
        }
    }

    public sealed interface OpenResult {
        record Opened(OverviewSession session) implements OpenResult {
            public Opened {
                Objects.requireNonNull(session, "session");
            }
        }

        record Rejected() implements OpenResult {
        }
    }
}
