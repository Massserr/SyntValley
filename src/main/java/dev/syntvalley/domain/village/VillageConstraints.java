package dev.syntvalley.domain.village;

/** Bounds shared by the Village domain and schema-1 persistence codec. */
public final class VillageConstraints {
    public static final int MAX_NAME_CODE_POINTS = 64;
    public static final int MAX_DIMENSION_ID_CHARACTERS = 128;

    private VillageConstraints() {
    }

    public static boolean isValidName(String name) {
        if (name == null || name.isBlank() || name.codePointCount(0, name.length()) > MAX_NAME_CODE_POINTS) {
            return false;
        }

        return name.codePoints().noneMatch(codePoint -> Character.isISOControl(codePoint)
                || codePoint == '§'
                || codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE);
    }

    public static boolean isValidDimensionId(String dimensionId) {
        if (dimensionId == null || dimensionId.isEmpty() || dimensionId.length() > MAX_DIMENSION_ID_CHARACTERS) {
            return false;
        }

        int separator = dimensionId.indexOf(':');
        if (separator <= 0 || separator == dimensionId.length() - 1 || separator != dimensionId.lastIndexOf(':')) {
            return false;
        }

        return isValidNamespace(dimensionId, 0, separator)
                && isValidPath(dimensionId, separator + 1, dimensionId.length());
    }

    private static boolean isValidNamespace(String value, int start, int end) {
        for (int index = start; index < end; index++) {
            char character = value.charAt(index);
            if (!(character >= 'a' && character <= 'z')
                    && !(character >= '0' && character <= '9')
                    && character != '_'
                    && character != '-'
                    && character != '.') {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidPath(String value, int start, int end) {
        for (int index = start; index < end; index++) {
            char character = value.charAt(index);
            if (!(character >= 'a' && character <= 'z')
                    && !(character >= '0' && character <= '9')
                    && character != '_'
                    && character != '-'
                    && character != '.'
                    && character != '/') {
                return false;
            }
        }
        return true;
    }
}
