package dev.syntvalley.persistence.saveddata;

public record VillagePolicyRecord(boolean autoAcceptSafeProjects, int maxActiveProjects) {
    public static final int DEFAULT_MAX_ACTIVE_PROJECTS = 2;

    public VillagePolicyRecord {
        if (maxActiveProjects < 1 || maxActiveProjects > PersistenceBounds.MAX_ACTIVE_PROJECTS) {
            throw new IllegalArgumentException("maxActiveProjects is outside schema bounds");
        }
    }

    public static VillagePolicyRecord defaults() {
        return new VillagePolicyRecord(false, DEFAULT_MAX_ACTIVE_PROJECTS);
    }
}
