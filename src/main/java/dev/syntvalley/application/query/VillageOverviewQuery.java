package dev.syntvalley.application.query;

import dev.syntvalley.application.port.CitizenStateRepository;
import dev.syntvalley.application.port.VillageStateRepository;
import dev.syntvalley.domain.citizen.CitizenAggregate;
import dev.syntvalley.domain.identity.VillageId;
import dev.syntvalley.domain.resource.ResourceKey;
import dev.syntvalley.domain.village.VillageAggregate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Builds a bounded Village overview read model from the canonical repositories on the server. */
public final class VillageOverviewQuery {
    public static final int MAX_OVERVIEW_RESIDENTS = 64;
    public static final int MAX_OVERVIEW_RESOURCES = 64;

    private final VillageStateRepository villages;
    private final CitizenStateRepository citizens;

    public VillageOverviewQuery(VillageStateRepository villages, CitizenStateRepository citizens) {
        this.villages = Objects.requireNonNull(villages, "villages");
        this.citizens = Objects.requireNonNull(citizens, "citizens");
    }

    public Optional<VillageOverviewDto> overview(VillageId villageId, Map<ResourceKey, Integer> resourceCounts) {
        Objects.requireNonNull(villageId, "villageId");
        Objects.requireNonNull(resourceCounts, "resourceCounts");
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
                    citizen.activeTask().map(task -> task.kind().name()).orElse("NONE"),
                    citizen.profession().map(active -> active.professionId().value()).orElse("NONE"),
                    citizen.profession().map(active -> active.level()).orElse(0)
            ));
        }

        List<VillageOverviewDto.ResourceSummaryEntry> resources = summariseResources(resourceCounts);

        return Optional.of(new VillageOverviewDto(
                village.id().toString(),
                village.name(),
                village.lifecycle().name(),
                village.revision(),
                village.coreLocation().isPresent(),
                residentCount,
                residentCount > residents.size(),
                residents,
                resources
        ));
    }

    /**
     * Orders the ledger counts deterministically by resource key and caps the sample so a village with a
     * huge variety of items can never blow up the overview payload.
     */
    private static List<VillageOverviewDto.ResourceSummaryEntry> summariseResources(Map<ResourceKey, Integer> resourceCounts) {
        TreeMap<String, Integer> ordered = new TreeMap<>();
        for (Map.Entry<ResourceKey, Integer> entry : resourceCounts.entrySet()) {
            int count = entry.getValue() == null ? 0 : entry.getValue();
            if (count > 0) {
                ordered.merge(entry.getKey().value(), count, Integer::sum);
            }
        }
        List<VillageOverviewDto.ResourceSummaryEntry> resources = new ArrayList<>(Math.min(ordered.size(), MAX_OVERVIEW_RESOURCES));
        for (Map.Entry<String, Integer> entry : ordered.entrySet()) {
            if (resources.size() >= MAX_OVERVIEW_RESOURCES) {
                break;
            }
            resources.add(new VillageOverviewDto.ResourceSummaryEntry(entry.getKey(), entry.getValue()));
        }
        return resources;
    }
}
