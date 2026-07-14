package dev.syntvalley.domain.personality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PersonalityPolicyTest {
    private final PersonalityPolicy policy = new PersonalityPolicy();

    @Test
    void moodBucketsFromNeeds() {
        assertEquals(Mood.LOW, Mood.fromNeeds(0, 0));
        assertEquals(Mood.LOW, Mood.fromNeeds(200, 200));
        assertEquals(Mood.NEUTRAL, Mood.fromNeeds(500, 500));
        assertEquals(Mood.HIGH, Mood.fromNeeds(800, 800));
    }

    @Test
    void diligenceRaisesWorkScoreMeasurably() {
        int high = policy.workScore(new Personality(100, 50), Mood.NEUTRAL);
        int low = policy.workScore(new Personality(0, 50), Mood.NEUTRAL);
        assertTrue(high > low, "more diligent citizens score work higher");
        assertTrue(high - low >= 30, "the difference is measurable, not noise");
    }

    @Test
    void sociabilitySwingsSocialVersusSolitary() {
        Personality social = new Personality(50, 100);
        Personality solitary = new Personality(50, 0);
        assertTrue(policy.socialRestScore(social, Mood.NEUTRAL) > policy.solitaryRestScore(social, Mood.NEUTRAL));
        assertTrue(policy.solitaryRestScore(solitary, Mood.NEUTRAL) > policy.socialRestScore(solitary, Mood.NEUTRAL));
    }

    @Test
    void moodIsMonotonic() {
        Personality personality = new Personality(60, 60);
        assertTrue(policy.workScore(personality, Mood.HIGH) >= policy.workScore(personality, Mood.NEUTRAL));
        assertTrue(policy.workScore(personality, Mood.NEUTRAL) >= policy.workScore(personality, Mood.LOW));
    }

    @Test
    void scoresAreBoundedAndDeterministic() {
        Personality personality = new Personality(100, 100);
        int first = policy.workScore(personality, Mood.HIGH);
        int second = policy.workScore(personality, Mood.HIGH);
        assertEquals(first, second, "same inputs reproduce the same score");
        assertTrue(first >= PersonalityPolicy.MIN_SCORE && first <= PersonalityPolicy.MAX_SCORE);
        assertTrue(policy.workScore(new Personality(0, 0), Mood.LOW) >= PersonalityPolicy.MIN_SCORE);
    }
}
