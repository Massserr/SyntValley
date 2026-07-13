package dev.syntvalley.domain.resource;

import dev.syntvalley.domain.identity.TaskId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Transient logical reservations against the ledger so two tasks never spend the same stock. A
 * reservation is owned by a task and expires; it is never trusted after restart (rebuilt from scratch).
 */
public final class ResourceReservations {
    private final Map<TaskId, Reservation> byOwner = new LinkedHashMap<>();

    /** Amount currently reserved for a key by every owner. */
    public int reservedFor(ResourceKey key) {
        Objects.requireNonNull(key, "key");
        int total = 0;
        for (Reservation reservation : byOwner.values()) {
            if (reservation.key().equals(key)) {
                total += reservation.amount();
            }
        }
        return total;
    }

    /**
     * Reserves {@code amount} of {@code key} for {@code owner} against the ledger {@code available}
     * count, refusing if it would exceed what other owners have not already reserved.
     */
    public boolean reserve(TaskId owner, ResourceKey key, int amount, int available, long expiryGameTime) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(key, "key");
        if (amount <= 0 || available < 0) {
            return false;
        }
        int reservedByOthers = 0;
        for (Map.Entry<TaskId, Reservation> entry : byOwner.entrySet()) {
            if (!entry.getKey().equals(owner) && entry.getValue().key().equals(key)) {
                reservedByOthers += entry.getValue().amount();
            }
        }
        if (reservedByOthers + amount > available) {
            return false;
        }
        byOwner.put(owner, new Reservation(key, amount, expiryGameTime));
        return true;
    }

    public Optional<Reservation> reservationOf(TaskId owner) {
        return Optional.ofNullable(byOwner.get(Objects.requireNonNull(owner, "owner")));
    }

    public boolean release(TaskId owner) {
        return byOwner.remove(Objects.requireNonNull(owner, "owner")) != null;
    }

    /** Removes reservations that expired at or before {@code now}, returning the released owners. */
    public List<TaskId> expire(long now) {
        List<TaskId> released = new ArrayList<>();
        byOwner.entrySet().removeIf(entry -> {
            boolean expired = now >= entry.getValue().expiryGameTime();
            if (expired) {
                released.add(entry.getKey());
            }
            return expired;
        });
        return List.copyOf(released);
    }

    public int size() {
        return byOwner.size();
    }

    public record Reservation(ResourceKey key, int amount, long expiryGameTime) {
        public Reservation {
            Objects.requireNonNull(key, "key");
            if (amount <= 0) {
                throw new IllegalArgumentException("amount must be positive");
            }
        }
    }
}
