package dev.syntvalley.ai.protocol.json;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * An immutable, already-bounded JSON tree produced by {@link StrictJson}. Numbers are integers only —
 * the strict parser rejects floats, NaN and Infinity — so the protocol schema never has to defend
 * against fractional or non-finite values.
 */
public sealed interface JsonValue {

    record JsonObject(Map<String, JsonValue> members) implements JsonValue {
        public JsonObject {
            members = Map.copyOf(Objects.requireNonNull(members, "members"));
        }

        public Optional<JsonValue> get(String key) {
            return Optional.ofNullable(members.get(key));
        }

        public boolean has(String key) {
            return members.containsKey(key);
        }
    }

    record JsonArray(List<JsonValue> elements) implements JsonValue {
        public JsonArray {
            elements = List.copyOf(Objects.requireNonNull(elements, "elements"));
        }
    }

    record JsonString(String value) implements JsonValue {
        public JsonString {
            Objects.requireNonNull(value, "value");
        }
    }

    /** JSON numbers are constrained to integers by the strict parser. */
    record JsonInteger(long value) implements JsonValue {
    }

    record JsonBoolean(boolean value) implements JsonValue {
    }

    record JsonNull() implements JsonValue {
    }
}
