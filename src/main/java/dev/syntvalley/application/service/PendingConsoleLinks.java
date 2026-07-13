package dev.syntvalley.application.service;

import dev.syntvalley.domain.identity.VillageId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Transient server-side selections for explicit Console linking: a player right-clicks a Synt Core
 * to select its Village, then right-clicks an unbound Console to bind it. Pure bookkeeping with a
 * per-selection expiry so abandoned selections do not linger.
 */
public final class PendingConsoleLinks {
    public static final int MAX_PENDING = 256;

    private final int maxPending;
    private final Map<UUID, Pending> pending = new LinkedHashMap<>();

    public PendingConsoleLinks() {
        this(MAX_PENDING);
    }

    public PendingConsoleLinks(int maxPending) {
        if (maxPending < 1) {
            throw new IllegalArgumentException("maxPending must be positive");
        }
        this.maxPending = maxPending;
    }

    /** Records a player's Village selection. A new player is dropped only when the registry is full. */
    public boolean select(UUID player, VillageId villageId, long expiryGameTime) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(villageId, "villageId");
        if (!pending.containsKey(player) && pending.size() >= maxPending) {
            return false;
        }
        pending.put(player, new Pending(villageId, expiryGameTime));
        return true;
    }

    /** Consumes a still-valid selection (removing it). Expired or missing selections return empty. */
    public Optional<VillageId> consume(UUID player, long gameTime) {
        Objects.requireNonNull(player, "player");
        Pending selection = pending.remove(player);
        if (selection == null || gameTime > selection.expiryGameTime()) {
            return Optional.empty();
        }
        return Optional.of(selection.villageId());
    }

    public boolean clear(UUID player) {
        return pending.remove(Objects.requireNonNull(player, "player")) != null;
    }

    public int size() {
        return pending.size();
    }

    private record Pending(VillageId villageId, long expiryGameTime) {
        private Pending {
            Objects.requireNonNull(villageId, "villageId");
        }
    }
}
