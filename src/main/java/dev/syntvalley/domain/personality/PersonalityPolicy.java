package dev.syntvalley.domain.personality;

import java.util.Objects;

/**
 * Turns personality + mood into bounded soft scores (0..100) for the optional choices a calm citizen
 * faces: whether to take a work shift, and whether to spend downtime socially or alone. These scores are
 * only ever compared among soft options — a critical need or safety task is chosen by the planner before
 * personality is even consulted, so a trait can never override safety.
 */
public final class PersonalityPolicy {
    public static final int MIN_SCORE = 0;
    public static final int MAX_SCORE = 100;

    public int workScore(Personality personality, Mood mood) {
        return score(traitOf(personality).diligence(), mood);
    }

    public int socialRestScore(Personality personality, Mood mood) {
        return score(traitOf(personality).sociability(), mood);
    }

    public int solitaryRestScore(Personality personality, Mood mood) {
        return score(Personality.MAX - traitOf(personality).sociability(), mood);
    }

    private static Personality traitOf(Personality personality) {
        return Objects.requireNonNull(personality, "personality");
    }

    private int score(int trait, Mood mood) {
        Objects.requireNonNull(mood, "mood");
        return clamp(trait * 6 / 10 + moodModifier(mood));
    }

    private static int moodModifier(Mood mood) {
        return switch (mood) {
            case HIGH -> 20;
            case NEUTRAL -> 0;
            case LOW -> -20;
        };
    }

    private static int clamp(int value) {
        return Math.max(MIN_SCORE, Math.min(MAX_SCORE, value));
    }
}
