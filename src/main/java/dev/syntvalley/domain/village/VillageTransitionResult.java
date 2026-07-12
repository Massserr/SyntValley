package dev.syntvalley.domain.village;

import java.util.Objects;

public sealed interface VillageTransitionResult {
    record Changed(VillageAggregate village) implements VillageTransitionResult {
        public Changed {
            Objects.requireNonNull(village, "village");
        }
    }

    record Unchanged(VillageAggregate village) implements VillageTransitionResult {
        public Unchanged {
            Objects.requireNonNull(village, "village");
        }
    }

    record Rejected(VillageTransitionRejection reason) implements VillageTransitionResult {
        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
