package dev.syntvalley.application.profession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.syntvalley.domain.profession.ProfessionDefinition;
import dev.syntvalley.domain.profession.ProfessionId;
import dev.syntvalley.domain.task.TaskKind;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProfessionCatalogTest {
    @Test
    void builtinHasWorkAndNonWorkProfessions() {
        ProfessionCatalog catalog = ProfessionCatalog.builtin();

        assertEquals(2, catalog.size());
        assertTrue(catalog.get(ProfessionCatalog.CARETAKER).orElseThrow().allows(TaskKind.WORK));
        assertFalse(catalog.get(ProfessionCatalog.WANDERER).orElseThrow().allows(TaskKind.WORK));
        assertTrue(catalog.get(new ProfessionId("syntvalley:unknown")).isEmpty());
    }

    @Test
    void catalogRejectsKeyThatDoesNotMatchDefinition() {
        ProfessionDefinition wanderer =
                new ProfessionDefinition(ProfessionCatalog.WANDERER, 1, Set.of(), 100, 1);
        assertThrows(IllegalArgumentException.class,
                () -> new ProfessionCatalog(Map.of(ProfessionCatalog.CARETAKER, wanderer)));
    }
}
