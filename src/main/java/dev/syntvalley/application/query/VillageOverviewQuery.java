package dev.syntvalley.application.query;

import dev.syntvalley.application.port.CitizenStateRepository;
import dev.syntvalley.application.port.VillageStateRepository;
import dev.syntvalley.domain.citizen.CitizenAggregate;
import dev.syntvalley.domain.identity.VillageId;
import dev.syntvalley.domain.village.VillageAggregate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Builds a bounded Village overview read model from the canonical repositories on the server. */
public final class VillageOverviewQuery {
    public static final int MAX_OVERVIEW_RESIDENTS = 64;

    private final VillageStateRepository villages;
    private final CitizenStateRepository citizens;

    public VillageOverviewQuery(VillageStateRepository villages, CitizenStateRepository citizens) {
        this.villages = Objects.requireNonNull(villages, "villages");
        this.citizens = Objects.requireNonNull(citizens, "citizens");
    }

    public Optional<VillageOverviewDto> overview(VillageId villageId) {
        Objects.requireNonNull(villageId, "villageId");
        if (!villages.isAvailable() || !citizens.isAvailable()) {
            return Optional.empty();
        }

        Optional<VillageAggregate> found = villages.find(villageId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        VillageAggregate village = found.orElseThrow();

        int residentCount = citizens.countForVillage(villageId);
        List<CitizenAggregate> sample = citizens.findByVillage(villageId, MAX_OVERVIEW_RESIDENTS);
        List<VillageOverviewDto.CitizenOverviewEntry> residents = new ArrayList<>(sample.size());
        for (CitizenAggregate citizen : sample) {
            residents.add(new VillageOverviewDto.CitizenOverviewEntry(
                    citizen.id().toString(),
                    citizen.name(),
                    citizen.lifecycle().name(),
                    citizen.boundEntityId().isPresent(),
                    citizen.needs().hunger(),
                    citizen.needs().rest(),
                    citizen.activeTask().map(task -> task.kind().name()).orElse("NONE")
            ));
        }

        return Optional.of(new VillageOverviewDto(
                village.id().toString(),
                village.name(),
                village.lifecycle().name(),
                village.revision(),
                village.coreLocation().isPresent(),
                residentCount,
                residentCount > residents.size(),
                residents
        ));
    }
}
