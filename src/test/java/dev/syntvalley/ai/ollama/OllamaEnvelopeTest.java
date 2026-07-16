package dev.syntvalley.ai.ollama;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OllamaEnvelopeTest {
    @Test
    void parsesDoneAndResponse() {
        OllamaEnvelope envelope = OllamaEnvelope.parse("{\"model\":\"m\",\"response\":\"hi there\",\"done\":true}");
        assertTrue(envelope.done());
        assertEquals("hi there", envelope.response());
    }

    @Test
    void doesNotConfuseKeywordsInsideTheResponseText() {
        String json = "{\"response\":\"the model wrote \\\"done\\\":false and \\\"response\\\"\",\"done\":true}";
        OllamaEnvelope envelope = OllamaEnvelope.parse(json);
        assertTrue(envelope.done(), "the real top-level done wins");
        assertEquals("the model wrote \"done\":false and \"response\"", envelope.response());
    }

    @Test
    void unescapesStringEscapes() {
        OllamaEnvelope envelope = OllamaEnvelope.parse("{\"response\":\"a\\nb\\t\\u0041\\\\end\",\"done\":true}");
        assertEquals("a\nb\tA\\end", envelope.response());
    }

    @Test
    void readsDoneFalse() {
        assertFalse(OllamaEnvelope.parse("{\"response\":\"partial\",\"done\":false}").done());
    }

    @Test
    void skipsNestedContainersIncludingBracesInStrings() {
        String json = "{\"context\":[1,2,{\"a\":\"}\",\"b\":[3]}],\"response\":\"ok\",\"done\":true}";
        OllamaEnvelope envelope = OllamaEnvelope.parse(json);
        assertEquals("ok", envelope.response());
        assertTrue(envelope.done());
    }

    @Test
    void rejectsMissingFields() {
        assertThrows(IllegalArgumentException.class, () -> OllamaEnvelope.parse("{\"done\":true}"));
        assertThrows(IllegalArgumentException.class, () -> OllamaEnvelope.parse("{\"response\":\"x\"}"));
    }

    @Test
    void rejectsMalformedJson() {
        assertThrows(RuntimeException.class, () -> OllamaEnvelope.parse("{not json"));
        assertThrows(RuntimeException.class, () -> OllamaEnvelope.parse(""));
        assertThrows(RuntimeException.class, () -> OllamaEnvelope.parse("{\"response\":\"unterminated,\"done\":true}"));
    }

    @Test
    void quoteRoundTripsNastyStrings() {
        String nasty = "line1\nline2\t\"quoted\" \\slash\\ and }brace{";
        OllamaEnvelope envelope =
                OllamaEnvelope.parse("{\"response\":" + OllamaEnvelope.quote(nasty) + ",\"done\":true}");
        assertEquals(nasty, envelope.response());
    }
}
