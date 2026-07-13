package dev.syntvalley.application.query;

import java.util.List;
import java.util.Objects;

/**
 * Bounded, transport-friendly read model of a Village. It carries only primitives and strings so the
 * Slice 4 network codec stays trivial and no persistent record or raw NBT ever crosses the wire.
 */
public record VillageOverviewDto(
        String villageId,
        String name,
        String lifecycle,
        long revision,
        boolean coreBound,
        int residentCount,
        boolean residentsTruncated,
        List<CitizenOverviewEntry> residents
) {
    public VillageOverviewDto {
        Objects.requireNonNull(villageId, "villageId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(lifecycle, "lifecycle");
        residents = List.copyOf(Objects.requireNonNull(residents, "residents"));
        if (revision < 1) {
            throw new IllegalArgumentException("revision must be positive");
        }
        if (residentCount < 0) {
            throw new IllegalArgumentException("residentCount must not be negative");
        }
        if (residents.size() > residentCount) {
            throw new IllegalArgumentException("resident sample cannot exceed the total count");
        }
        if (residentsTruncated == (residents.size() == residentCount)) {
            throw new IllegalArgumentException("residentsTruncated must reflect count vs sample size");
        }
    }

    public record CitizenOverviewEntry(
            String citizenId,
            String name,
            String lifecycle,
            boolean present,
            int hunger,
            int rest,
            String activity,
            String profession,
            int professionLevel
    ) {
        public CitizenOverviewEntry {
            Objects.requireNonNull(citizenId, "citizenId");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(lifecycle, "lifecycle");
            Objects.requireNonNull(activity, "activity");
            Objects.requireNonNull(profession, "profession");
        }
    }
}
