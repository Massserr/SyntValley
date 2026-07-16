package dev.syntvalley.ai.protocol.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StrictJsonTest {
    private static final StrictJsonLimits V1 = StrictJsonLimits.v1();

    private static JsonValue.JsonObject parse(String text) {
        return StrictJson.parse(text, V1);
    }

    private static void rejects(String text) {
        assertThrows(StrictJsonException.class, () -> parse(text));
    }

    @Test
    void parsesNestedObjectArrayAndScalars() {
        JsonValue.JsonObject root = parse("{\"a\":1,\"b\":[true,null,\"x\"],\"c\":{\"d\":-5}}");
        assertEquals(new JsonValue.JsonInteger(1), root.get("a").orElseThrow());
        JsonValue.JsonArray array = assertInstanceOf(JsonValue.JsonArray.class, root.get("b").orElseThrow());
        assertEquals(3, array.elements().size());
        assertEquals(new JsonValue.JsonBoolean(true), array.elements().get(0));
        assertInstanceOf(JsonValue.JsonNull.class, array.elements().get(1));
        JsonValue.JsonObject nested = assertInstanceOf(JsonValue.JsonObject.class, root.get("c").orElseThrow());
        assertEquals(new JsonValue.JsonInteger(-5), nested.get("d").orElseThrow());
    }

    @Test
    void parsesEmptyContainersAndBigIntegers() {
        assertTrue(parse("{}").members().isEmpty());
        assertEquals(new JsonValue.JsonInteger(9_007_199_254_740_991L),
                parse("{\"n\":9007199254740991}").get("n").orElseThrow());
        assertTrue(parse("{\"e\":[]}").has("e"));
    }

    @Test
    void rejectsNonObjectRoot() {
        rejects("[1,2]");
        rejects("\"x\"");
        rejects("42");
        rejects("true");
    }

    @Test
    void rejectsTrailingContentAndMarkdownFences() {
        rejects("{} extra");
        rejects("{}{}");
        rejects("```json\n{}\n```");
    }

    @Test
    void rejectsDuplicateKeys() {
        rejects("{\"a\":1,\"a\":2}");
    }

    @Test
    void rejectsNonIntegerAndNonFiniteNumbers() {
        rejects("{\"a\":1.5}");
        rejects("{\"a\":1e3}");
        rejects("{\"a\":NaN}");
        rejects("{\"a\":Infinity}");
    }

    @Test
    void rejectsRawControlCharacterInString() {
        rejects("{\"a\":\"line" + ((char) 1) + "\"}");
    }

    @Test
    void rejectsUnterminatedContainersAndStrings() {
        rejects("{\"a\":1");
        rejects("{\"a\":\"x");
        rejects("{\"a\":[1,2");
    }

    @Test
    void rejectsTrailingCommaAndMissingColon() {
        rejects("{\"a\":1,}");
        rejects("{\"a\" 1}");
    }

    @Test
    void enforcesDepthCap() {
        StrictJsonLimits shallow = new StrictJsonLimits(1024, 3, 32);
        StrictJson.parse("{\"a\":{\"b\":1}}", shallow); // value at depth 3 is allowed
        assertThrows(StrictJsonException.class,
                () -> StrictJson.parse("{\"a\":{\"b\":{\"c\":1}}}", shallow));
    }

    @Test
    void enforcesPropertyCap() {
        StrictJsonLimits narrow = new StrictJsonLimits(1024, 8, 2);
        StrictJson.parse("{\"a\":1,\"b\":2}", narrow);
        assertThrows(StrictJsonException.class,
                () -> StrictJson.parse("{\"a\":1,\"b\":2,\"c\":3}", narrow));
    }

    @Test
    void enforcesCharCap() {
        StrictJsonLimits tiny = new StrictJsonLimits(4, 8, 32);
        assertThrows(StrictJsonException.class, () -> StrictJson.parse("{\"aa\":1}", tiny));
    }
}
