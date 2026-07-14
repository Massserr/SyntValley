package dev.syntvalley.domain.personality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PersonalityTest {
    @Test
    void rejectsOutOfRangeTraits() {
        assertThrows(IllegalArgumentException.class, () -> new Personality(-1, 50));
        assertThrows(IllegalArgumentException.class, () -> new Personality(50, 101));
    }

    @Test
    void defaultsAreBalanced() {
        assertEquals(new Personality(50, 50), Personality.defaults());
    }

    @Test
    void fromSeedIsDeterministicAndBounded() {
        Personality first = Personality.fromSeed(123_456_789L);
        Personality second = Personality.fromSeed(123_456_789L);
        assertEquals(first, second, "same seed reproduces the same personality");
        assertTrue(first.diligence() >= Personality.MIN && first.diligence() <= Personality.MAX);
        assertTrue(first.sociability() >= Personality.MIN && first.sociability() <= Personality.MAX);
    }

    @Test
    void differentSeedsGiveDifferentPersonalities() {
        assertNotEquals(Personality.fromSeed(1L), Personality.fromSeed(2L));
    }
}
