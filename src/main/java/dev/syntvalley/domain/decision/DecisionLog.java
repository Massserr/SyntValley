package dev.syntvalley.domain.decision;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * A bounded, append-only audit log of decisions for one scope (a village). The newest entries are kept
 * and the oldest are dropped once the cap is reached; reads are paginated newest-first via a stable
 * sequence cursor, so the read-only UI never has to page over the whole log or expose raw internals.
 */
public final class DecisionLog {
    private final int maxRecords;
    private final Deque<DecisionRecord> records = new ArrayDeque<>();
    private long nextSequence = 1;

    public DecisionLog(int maxRecords) {
        if (maxRecords < 1) {
            throw new IllegalArgumentException("maxRecords must be positive");
        }
        this.maxRecords = maxRecords;
    }

    public DecisionRecord record(
            DecisionKind kind, String subject, String chosen, DecisionSource source, String reason, long gameTime) {
        DecisionRecord entry = new DecisionRecord(nextSequence++, kind, subject, chosen, source, reason, gameTime);
        records.addLast(entry);
        while (records.size() > maxRecords) {
            records.removeFirst();
        }
        return entry;
    }

    /** Up to {@code size} entries with sequence &lt; {@code beforeSequence}, newest first. */
    public List<DecisionRecord> page(long beforeSequence, int size) {
        if (size <= 0) {
            return List.of();
        }
        List<DecisionRecord> result = new ArrayList<>(Math.min(size, records.size()));
        Iterator<DecisionRecord> newestFirst = records.descendingIterator();
        while (newestFirst.hasNext() && result.size() < size) {
            DecisionRecord entry = newestFirst.next();
            if (entry.sequence() < beforeSequence) {
                result.add(entry);
            }
        }
        return List.copyOf(result);
    }

    public List<DecisionRecord> recent(int size) {
        return page(Long.MAX_VALUE, size);
    }

    public int size() {
        return records.size();
    }

    public List<DecisionRecord> all() {
        return List.copyOf(records);
    }
}
