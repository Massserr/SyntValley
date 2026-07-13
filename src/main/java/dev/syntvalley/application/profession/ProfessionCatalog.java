package dev.syntvalley.application.profession;

import dev.syntvalley.domain.profession.ProfessionDefinition;
import dev.syntvalley.domain.profession.ProfessionId;
import dev.syntvalley.domain.task.TaskKind;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Registry of profession definitions looked up by id (never an enum/class switch). Built-in for now;
 * a later refinement can populate it from data pack JSON without changing any caller.
 */
public final class ProfessionCatalog {
    public static final ProfessionId CARETAKER = new ProfessionId("syntvalley:caretaker");
    public static final ProfessionId WANDERER = new ProfessionId("syntvalley:wanderer");

    private final Map<ProfessionId, ProfessionDefinition> definitions;

    public ProfessionCatalog(Map<ProfessionId, ProfessionDefinition> definitions) {
        Objects.requireNonNull(definitions, "definitions");
        LinkedHashMap<ProfessionId, ProfessionDefinition> copy = new LinkedHashMap<>();
        definitions.forEach((id, definition) -> {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(definition, "definition");
            if (!id.equals(definition.id())) {
                throw new IllegalArgumentException("catalog key does not match definition id");
            }
            copy.put(id, definition);
        });
        this.definitions = Map.copyOf(copy);
    }

    public static ProfessionCatalog builtin() {
        return new ProfessionCatalog(Map.of(
                CARETAKER, new ProfessionDefinition(CARETAKER, 1, Set.of(TaskKind.WORK), 100, 5),
                WANDERER, new ProfessionDefinition(WANDERER, 1, Set.of(), 100, 1)));
    }

    public Optional<ProfessionDefinition> get(ProfessionId id) {
        return Optional.ofNullable(definitions.get(Objects.requireNonNull(id, "id")));
    }

    public boolean contains(ProfessionId id) {
        return definitions.containsKey(Objects.requireNonNull(id, "id"));
    }

    public int size() {
        return definitions.size();
    }
}
