package dev.syntvalley.application.port;

import dev.syntvalley.domain.citizen.CitizenAggregate;
import java.util.Objects;
import java.util.Optional;

public sealed interface CitizenCommitResult {
    record Committed(CitizenAggregate citizen) implements CitizenCommitResult {
        public Committed {
            Objects.requireNonNull(citizen, "citizen");
        }
    }

    record Rejected(RepositoryRejection reason, Optional<CitizenAggregate> current) implements CitizenCommitResult {
        public Rejected {
            Objects.requireNonNull(reason, "reason");
            current = Objects.requireNonNull(current, "current");
        }

        public Rejected(RepositoryRejection reason) {
            this(reason, Optional.empty());
        }
    }
}
