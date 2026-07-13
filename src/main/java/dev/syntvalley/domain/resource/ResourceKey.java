package dev.syntvalley.domain.resource;

import java.util.Objects;

/**
 * Typed resource key: a namespaced item id ({@code namespace:path}). Minecraft-free; the adapter maps
 * it to and from an Item/ResourceLocation. Tag keys can be added later without changing callers.
 */
public record ResourceKey(String value) {
    public ResourceKey {
        Objects.requireNonNull(value, "value");
        if (!isValid(value)) {
            throw new IllegalArgumentException("Invalid resource key: " + value);
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
        return isNamespace(value, 0, separator) && isPath(value, separator + 1, value.length());
    }

    private static boolean isNamespace(String value, int start, int end) {
        for (int index = start; index < end; index++) {
            char character = value.charAt(index);
            if (!(character >= 'a' && character <= 'z')
                    && !(character >= '0' && character <= '9')
                    && character != '_' && character != '-' && character != '.') {
                return false;
            }
        }
        return true;
    }

    private static boolean isPath(String value, int start, int end) {
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
