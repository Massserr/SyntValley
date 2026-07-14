package dev.syntvalley.application.port;

import dev.syntvalley.domain.project.BuildProject;
import dev.syntvalley.domain.project.ProjectId;
import java.util.List;
import java.util.Optional;

/**
 * Canonical persistence of build projects, accessed on the server thread. Update uses optimistic
 * concurrency (revision must advance by exactly one) so a stale write is refused, never silently applied.
 */
public interface ProjectStateRepository {
    boolean isAvailable();

    Optional<BuildProject> find(ProjectId id);

    /** All persisted projects (bounded); callers filter for active ones. */
    List<BuildProject> all();

    boolean create(BuildProject project);

    boolean update(BuildProject project, long expectedRevision);
}
