package dev.syntvalley.domain.profession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.syntvalley.domain.task.TaskKind;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProfessionTest {
    private static final ProfessionId CARETAKER = new ProfessionId("syntvalley:caretaker");
    private static final ProfessionDefinition DEFINITION =
            new ProfessionDefinition(CARETAKER, 1, Set.of(TaskKind.WORK), 100, 3);

    @Test
    void professionIdRejectsMalformedValues() {
        assertTrue(ProfessionId.isValid("syntvalley:caretaker"));
        assertFalse(ProfessionId.isValid("Caretaker"));
        assertFalse(ProfessionId.isValid("syntvalley:"));
        assertFalse(ProfessionId.isValid(":caretaker"));
        assertFalse(ProfessionId.isValid("a:b:c"));
        assertThrows(IllegalArgumentException.class, () -> new ProfessionId("BAD"));
    }

    @Test
    void definitionGatesWorkTaskKinds() {
        assertTrue(DEFINITION.allows(TaskKind.WORK));
        assertFalse(DEFINITION.allows(TaskKind.IDLE));
    }

    @Test
    void experienceLevelsUpThenCapsAtMax() {
        CitizenProfession fresh = CitizenProfession.assign(CARETAKER, 40);
        assertEquals(1, fresh.level());
        assertEquals(0, fresh.experience());

        CitizenProfession leveled = fresh.gainExperience(150, DEFINITION);
        assertEquals(2, leveled.level());
        assertEquals(50, leveled.experience());

        CitizenProfession maxed = leveled.gainExperience(60, DEFINITION);
        assertEquals(3, maxed.level(), "reaches max level");
        assertEquals(0, maxed.experience(), "experience stops accumulating at max level");
    }

    @Test
    void experienceIsANoOpAtMaxLevel() {
        CitizenProfession maxed = CitizenProfession.assign(CARETAKER, 0).gainExperience(1000, DEFINITION);
        assertEquals(3, maxed.level());
        assertSame(maxed, maxed.gainExperience(500, DEFINITION), "no grind writes once maxed");
    }
}
