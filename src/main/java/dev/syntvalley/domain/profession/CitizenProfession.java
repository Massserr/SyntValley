package dev.syntvalley.domain.profession;

import java.util.Objects;

/**
 * A citizen's assigned profession and progress. Experience accrues toward the next level and is
 * bounded: at the definition's max level it stops accumulating so work never grinds per-tick writes.
 */
public record CitizenProfession(ProfessionId professionId, int level, long experience, long changedGameTime) {
    public CitizenProfession {
        Objects.requireNonNull(professionId, "professionId");
        if (level < 1) {
            throw new IllegalArgumentException("level must be positive");
        }
        if (experience < 0) {
            throw new IllegalArgumentException("experience must not be negative");
        }
        if (changedGameTime < 0) {
            throw new IllegalArgumentException("changedGameTime must not be negative");
        }
    }

    public static CitizenProfession assign(ProfessionId professionId, long gameTime) {
        return new CitizenProfession(professionId, 1, 0, gameTime);
    }

    /** Awards experience against the given definition, applying level-ups and the max-level cap. */
    public CitizenProfession gainExperience(int amount, ProfessionDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (amount < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        if (!professionId.equals(definition.id())) {
            throw new IllegalArgumentException("definition does not match this profession");
        }

        int newLevel = level;
        long newExperience = experience + amount;
        while (newLevel < definition.maxLevel() && newExperience >= definition.experiencePerLevel()) {
            newExperience -= definition.experiencePerLevel();
            newLevel++;
        }
        if (newLevel >= definition.maxLevel()) {
            newExperience = 0;
        }
        if (newLevel == level && newExperience == experience) {
            return this;
        }
        return new CitizenProfession(professionId, newLevel, newExperience, changedGameTime);
    }
}
