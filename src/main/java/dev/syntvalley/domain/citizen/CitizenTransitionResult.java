package dev.syntvalley.domain.citizen;

import java.util.Objects;

public sealed interface CitizenTransitionResult {
    record Changed(CitizenAggregate citizen) implements CitizenTransitionResult {
        public Changed {
            Objects.requireNonNull(citizen, "citizen");
        }
    }

    record Unchanged(CitizenAggregate citizen) implements CitizenTransitionResult {
        public Unchanged {
            Objects.requireNonNull(citizen, "citizen");
        }
    }

    record Rejected(CitizenTransitionRejection reason) implements CitizenTransitionResult {
        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
