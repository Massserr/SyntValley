package dev.syntvalley.persistence.saveddata;

import dev.syntvalley.domain.citizen.CitizenConstraints;
import dev.syntvalley.domain.village.VillageConstraints;

public final class PersistenceBounds {
    public static final int MAX_VILLAGES = 4_096;
    public static final int MAX_CITIZENS = 65_536;
    public static final int MAX_PROJECTS = 16_384;
    public static final int MAX_VILLAGE_NAME_CODE_POINTS = VillageConstraints.MAX_NAME_CODE_POINTS;
    public static final int MAX_CITIZEN_NAME_CODE_POINTS = CitizenConstraints.MAX_NAME_CODE_POINTS;
    public static final int MAX_DIMENSION_ID_CHARACTERS = VillageConstraints.MAX_DIMENSION_ID_CHARACTERS;
    public static final int MAX_ACTIVE_PROJECTS = 16;
    public static final int MAX_FAILURE_DIAGNOSTIC_CHARACTERS = 256;

    /** Retention caps for Slice 9 village memory and the decision audit log. */
    public static final int MAX_MEMORIES_PER_VILLAGE = 64;
    public static final int MAX_PINNED_MEMORIES_PER_VILLAGE = 8;
    public static final int MAX_DECISIONS_PER_VILLAGE = 128;
    public static final int MAX_LOG_TEXT_CHARACTERS = 256;

    /**
     * Upper bound on simultaneously pending dirty keys: every village, citizen and project at once,
     * plus each village's memory store and decision log.
     */
    public static final int MAX_DIRTY_KEYS = MAX_VILLAGES + MAX_CITIZENS + MAX_PROJECTS + 2 * MAX_VILLAGES;

    private PersistenceBounds() {
    }
}
