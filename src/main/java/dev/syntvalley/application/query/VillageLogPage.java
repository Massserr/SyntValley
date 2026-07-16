package dev.syntvalley.application.query;

import java.util.List;
import java.util.Objects;

/**
 * One bounded, pre-rendered page of the read-only village log screen: the ranked memory lines (sent on
 * the first page only) and one page of decision lines with a cursor for older entries. Lines are plain
 * strings so the network codec stays trivial and no raw domain internals cross the wire.
 */
public record VillageLogPage(
        boolean firstPage,
        List<String> memoryLines,
        List<String> decisionLines,
        long nextCursor,
        boolean hasMore
) {
    public VillageLogPage {
        memoryLines = List.copyOf(Objects.requireNonNull(memoryLines, "memoryLines"));
        decisionLines = List.copyOf(Objects.requireNonNull(decisionLines, "decisionLines"));
        if (nextCursor < 0) {
            throw new IllegalArgumentException("nextCursor must not be negative");
        }
    }
}
