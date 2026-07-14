package dev.syntvalley.domain.project;

import java.util.Objects;

/** Result of a build project transition: either a new project revision or a typed rejection. */
public sealed interface ProjectTransitionResult {
    record Changed(BuildProject project) implements ProjectTransitionResult {
        public Changed {
            Objects.requireNonNull(project, "project");
        }
    }

    record Rejected(ProjectTransitionRejection reason) implements ProjectTransitionResult {
        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
