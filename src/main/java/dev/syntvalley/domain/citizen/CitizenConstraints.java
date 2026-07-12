package dev.syntvalley.domain.citizen;

/** Bounds shared by the Citizen domain and the schema-1 persistence codec added in Slice 3. */
public final class CitizenConstraints {
    public static final int MAX_NAME_CODE_POINTS = 32;

    /** Gameplay cap on simultaneously ACTIVE citizens bound to a single Village. */
    public static final int MAX_CITIZENS_PER_VILLAGE = 32;

    private CitizenConstraints() {
    }

    public static boolean isValidName(String name) {
        if (name == null || name.isBlank() || name.codePointCount(0, name.length()) > MAX_NAME_CODE_POINTS) {
            return false;
        }

        return name.codePoints().noneMatch(codePoint -> Character.isISOControl(codePoint)
                || codePoint == '§'
                || codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE);
    }
}
