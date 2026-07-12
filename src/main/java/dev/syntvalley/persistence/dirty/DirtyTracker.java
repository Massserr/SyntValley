package dev.syntvalley.persistence.dirty;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Bounded, deduplicating bookkeeping. Canonical state never lives only in this tracker. */
public final class DirtyTracker {
    private final int capacity;
    private final Runnable markRootDirty;
    private final LinkedHashMap<DirtyKey, EnumSet<DirtyReason>> pending = new LinkedHashMap<>();

    public DirtyTracker(int capacity, Runnable markRootDirty) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Dirty tracker capacity must be positive");
        }
        this.capacity = capacity;
        this.markRootDirty = Objects.requireNonNull(markRootDirty, "markRootDirty");
    }

    public boolean canAccept(DirtyKey key) {
        Objects.requireNonNull(key, "key");
        return pending.containsKey(key) || pending.size() < capacity;
    }

    public void mark(DirtyKey key, DirtyReason reason) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(reason, "reason");
        if (!canAccept(key)) {
            throw new IllegalStateException("Dirty tracker hard limit reached without a deduplication slot");
        }
        pending.computeIfAbsent(key, ignored -> EnumSet.noneOf(DirtyReason.class)).add(reason);
        markRootDirty.run();
    }

    public int pendingCount() {
        return pending.size();
    }

    public List<DirtyEntry> drain(int maximum) {
        if (maximum < 0) {
            throw new IllegalArgumentException("Drain maximum must not be negative");
        }
        if (maximum == 0 || pending.isEmpty()) {
            return List.of();
        }

        int count = Math.min(maximum, pending.size());
        ArrayList<DirtyEntry> drained = new ArrayList<>(count);
        var iterator = pending.entrySet().iterator();
        while (iterator.hasNext() && drained.size() < count) {
            Map.Entry<DirtyKey, EnumSet<DirtyReason>> entry = iterator.next();
            drained.add(new DirtyEntry(entry.getKey(), entry.getValue()));
            iterator.remove();
        }
        return List.copyOf(drained);
    }

    public List<DirtyEntry> drainAll() {
        return drain(pending.size());
    }
}
