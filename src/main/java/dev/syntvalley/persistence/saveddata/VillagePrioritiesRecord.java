package dev.syntvalley.persistence.saveddata;

public record VillagePrioritiesRecord(
        int food,
        int safety,
        int materials,
        int housing,
        int tools,
        int social
) {
    public static final int DEFAULT_PRIORITY = 50;

    public VillagePrioritiesRecord {
        requirePriority(food, "food");
        requirePriority(safety, "safety");
        requirePriority(materials, "materials");
        requirePriority(housing, "housing");
        requirePriority(tools, "tools");
        requirePriority(social, "social");
    }

    public static VillagePrioritiesRecord defaults() {
        return new VillagePrioritiesRecord(
                DEFAULT_PRIORITY,
                DEFAULT_PRIORITY,
                DEFAULT_PRIORITY,
                DEFAULT_PRIORITY,
                DEFAULT_PRIORITY,
                DEFAULT_PRIORITY
        );
    }

    private static void requirePriority(int value, String name) {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException(name + " priority must be between 0 and 100");
        }
    }
}
