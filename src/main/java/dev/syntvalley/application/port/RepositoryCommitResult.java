package dev.syntvalley.application.port;

import dev.syntvalley.domain.village.VillageAggregate;
import java.util.Objects;
import java.util.Optional;

public sealed interface RepositoryCommitResult {
    record Committed(VillageAggregate village) implements RepositoryCommitResult {
        public Committed {
            Objects.requireNonNull(village, "village");
        }
    }

    record Rejected(RepositoryRejection reason, Optional<VillageAggregate> current) implements RepositoryCommitResult {
        public Rejected {
            Objects.requireNonNull(reason, "reason");
            current = Objects.requireNonNull(current, "current");
        }

        public Rejected(RepositoryRejection reason) {
            this(reason, Optional.empty());
        }
    }
}
