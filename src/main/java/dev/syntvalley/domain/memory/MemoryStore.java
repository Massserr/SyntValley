package dev.syntvalley.domain.memory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A bounded, deduplicated memory store for one scope (a citizen or a village). Adding the same event
 * twice is idempotent, so replay/restart never duplicates a memory. Salience decays over time; when the
 * store is over capacity the least-salient un-pinned record is evicted. Pinned records (bounded by a cap)
 * never decay and are never evicted, so identity/project memories are never lost to retention.
 */
public final class MemoryStore {
    private final int maxRecords;
    private final int maxPinned;
    private final Map<String, MemoryRecord> byKey = new LinkedHashMap<>();

    public MemoryStore(int maxRecords, int maxPinned) {
        if (maxRecords < 1) {
            throw new IllegalArgumentException("maxRecords must be positive");
        }
        if (maxPinned < 0 || maxPinned > maxRecords) {
            throw new IllegalArgumentException("maxPinned out of range");
        }
        this.maxRecords = maxRecords;
        this.maxPinned = maxPinned;
    }

    /** Adds a memory unless its dedupe key already exists; returns whether it was newly added. */
    public boolean add(MemoryRecord record) {
        Objects.requireNonNull(record, "record");
        if (byKey.containsKey(record.dedupeKey())) {
            return false; // same event id — no duplicate on replay/restart
        }
        byKey.put(record.dedupeKey(), record);
        evictWhileOverCapacity();
        return true;
    }

    /** Reduces the salience of every un-pinned memory by {@code amount}, forgetting any that reach zero. */
    public void decay(int amount) {
        if (amount <= 0) {
            return;
        }
        byKey.entrySet().removeIf(entry -> {
            MemoryRecord record = entry.getValue();
            if (record.pinned()) {
                return false;
            }
            int reduced = record.salience() - amount;
            if (reduced <= MemoryRecord.MIN_SALIENCE) {
                return true; // faded away
            }
            entry.setValue(record.withSalience(reduced));
            return false;
        });
    }

    /** Pins a memory so it never decays or is evicted; refuses beyond the pinned cap. */
    public boolean pin(String dedupeKey) {
        MemoryRecord record = byKey.get(Objects.requireNonNull(dedupeKey, "dedupeKey"));
        if (record == null) {
            return false;
        }
        if (record.pinned()) {
            return true;
        }
        if (pinnedCount() >= maxPinned) {
            return false;
        }
        byKey.put(dedupeKey, record.withPinned(true));
        return true;
    }

    public Optional<MemoryRecord> find(String dedupeKey) {
        return Optional.ofNullable(byKey.get(Objects.requireNonNull(dedupeKey, "dedupeKey")));
    }

    /** Records ordered for display: pinned first, then most salient, then oldest. */
    public List<MemoryRecord> ranked() {
        List<MemoryRecord> all = new ArrayList<>(byKey.values());
        all.sort(Comparator.comparing(MemoryRecord::pinned).reversed()
                .thenComparing(Comparator.comparingInt(MemoryRecord::salience).reversed())
                .thenComparingLong(MemoryRecord::createdGameTime));
        return List.copyOf(all);
    }

    public int size() {
        return byKey.size();
    }

    public int pinnedCount() {
        int count = 0;
        for (MemoryRecord record : byKey.values()) {
            if (record.pinned()) {
                count++;
            }
        }
        return count;
    }

    public List<MemoryRecord> records() {
        return List.copyOf(byKey.values());
    }

    private void evictWhileOverCapacity() {
        while (byKey.size() > maxRecords) {
            String victim = byKey.values().stream()
                    .filter(record -> !record.pinned())
                    .min(Comparator.comparingInt(MemoryRecord::salience)
                            .thenComparingLong(MemoryRecord::createdGameTime))
                    .map(MemoryRecord::dedupeKey)
                    .orElse(null);
            if (victim == null) {
                break; // everything left is pinned
            }
            byKey.remove(victim);
        }
    }
}
