package dev.syntvalley.domain.building;

import java.util.Objects;

/** Namespaced building template id, e.g. {@code syntvalley:small_storehouse}. Minecraft-free. */
public record BuildingTemplateId(String value) {
    public BuildingTemplateId {
        Objects.requireNonNull(value, "value");
        if (!isValid(value)) {
            throw new IllegalArgumentException("Invalid building template id: " + value);
        }
    }

    public static boolean isValid(String value) {
        if (value == null) {
            return false;
        }
        int separator = value.indexOf(':');
        if (separator <= 0 || separator == value.length() - 1 || separator != value.lastIndexOf(':')) {
            return false;
        }
        return isSegment(value, 0, separator) && isSegment(value, separator + 1, value.length());
    }

    private static boolean isSegment(String value, int start, int end) {
        for (int index = start; index < end; index++) {
            char character = value.charAt(index);
            if (!(character >= 'a' && character <= 'z')
                    && !(character >= '0' && character <= '9')
                    && character != '_' && character != '-' && character != '.' && character != '/') {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return value;
    }
}
