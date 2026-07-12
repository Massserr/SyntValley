package dev.syntvalley.persistence.saveddata;

import dev.syntvalley.domain.village.VillageConstraints;

public final class PersistenceBounds {
    public static final int MAX_VILLAGES = 4_096;
    public static final int MAX_VILLAGE_NAME_CODE_POINTS = VillageConstraints.MAX_NAME_CODE_POINTS;
    public static final int MAX_DIMENSION_ID_CHARACTERS = VillageConstraints.MAX_DIMENSION_ID_CHARACTERS;
    public static final int MAX_ACTIVE_PROJECTS = 16;
    public static final int MAX_FAILURE_DIAGNOSTIC_CHARACTERS = 256;

    private PersistenceBounds() {
    }
}
