package dev.syntvalley.domain.personality;

/**
 * A citizen's stable, bounded traits. Traits only ever nudge soft task scores — never a hard safety or
 * critical-need decision. Defaults are derived deterministically from the citizen's identity, so the
 * same citizen always has the same personality across restarts.
 */
public record Personality(int diligence, int sociability) {
    public static final int MIN = 0;
    public static final int MAX = 100;

    public Personality {
        if (!inRange(diligence) || !inRange(sociability)) {
            throw new IllegalArgumentException("traits must be within " + MIN + ".." + MAX);
        }
    }

    public static Personality defaults() {
        return new Personality(50, 50);
    }

    /** Deterministic, well-spread traits from a seed (e.g. the citizen's UUID bits). */
    public static Personality fromSeed(long seed) {
        int diligence = (int) Long.remainderUnsigned(mix(seed, 0x9E3779B97F4A7C15L), MAX + 1);
        int sociability = (int) Long.remainderUnsigned(mix(seed, 0xC2B2AE3D27D4EB4FL), MAX + 1);
        return new Personality(diligence, sociability);
    }

    private static boolean inRange(int value) {
        return value >= MIN && value <= MAX;
    }

    private static long mix(long seed, long salt) {
        long h = seed ^ salt;
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= (h >>> 33);
        return h;
    }
}
