package dev.syntvalley.application.service;

import dev.syntvalley.domain.project.BuildProject;
import java.util.Objects;

/** Outcome of proposing a build project: either an admitted project or a typed rejection. */
public sealed interface ProposeResult {
    record Proposed(BuildProject project) implements ProposeResult {
        public Proposed {
            Objects.requireNonNull(project, "project");
        }
    }

    record Rejected(ProposalRejection reason) implements ProposeResult {
        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
