package dev.syntvalley.ai.protocol.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * A strict, bounded JSON parser for untrusted LLM output (protocol §5). It accepts exactly one top-level
 * object and rejects everything the contract forbids: trailing content, Markdown fences, duplicate keys,
 * non-integer / NaN / Infinity numbers, raw control characters in strings, and anything past the depth,
 * property or size caps — with the caps enforced during the walk, before a large graph can be built.
 */
public final class StrictJson {
    private final String source;
    private final StrictJsonLimits limits;
    private int position;

    private StrictJson(String source, StrictJsonLimits limits) {
        this.source = source;
        this.limits = limits;
    }

    /** Parses a single top-level JSON object, or throws {@link StrictJsonException} on any violation. */
    public static JsonValue.JsonObject parse(String text, StrictJsonLimits limits) {
        if (text == null) {
            throw new StrictJsonException("null input");
        }
        if (text.length() > limits.maxChars()) {
            throw new StrictJsonException("input exceeds char cap");
        }
        StrictJson parser = new StrictJson(text, limits);
        parser.skipWhitespace();
        if (!parser.peekIs('{')) {
            throw new StrictJsonException("root must be a JSON object");
        }
        JsonValue value = parser.parseValue(1);
        parser.skipWhitespace();
        if (parser.position != text.length()) {
            throw new StrictJsonException("trailing content after root object");
        }
        return (JsonValue.JsonObject) value;
    }

    private JsonValue parseValue(int depth) {
        if (depth > limits.maxDepth()) {
            throw new StrictJsonException("nesting exceeds depth cap");
        }
        skipWhitespace();
        if (position >= source.length()) {
            throw new StrictJsonException("unexpected end of input");
        }
        char character = source.charAt(position);
        return switch (character) {
            case '{' -> parseObject(depth);
            case '[' -> parseArray(depth);
            case '"' -> new JsonValue.JsonString(parseString());
            case 't', 'f' -> parseBoolean();
            case 'n' -> parseNull();
            default -> {
                if (character == '-' || (character >= '0' && character <= '9')) {
                    yield parseNumber();
                }
                throw new StrictJsonException("unexpected character '" + character + "'");
            }
        };
    }

    private JsonValue parseObject(int depth) {
        expect('{');
        LinkedHashMap<String, JsonValue> members = new LinkedHashMap<>();
        skipWhitespace();
        if (peekIs('}')) {
            position++;
            return new JsonValue.JsonObject(members);
        }
        while (true) {
            skipWhitespace();
            if (!peekIs('"')) {
                throw new StrictJsonException("object key must be a string");
            }
            String key = parseString();
            if (members.containsKey(key)) {
                throw new StrictJsonException("duplicate key: " + key);
            }
            if (members.size() + 1 > limits.maxProperties()) {
                throw new StrictJsonException("object exceeds property cap");
            }
            skipWhitespace();
            expect(':');
            members.put(key, parseValue(depth + 1));
            skipWhitespace();
            char separator = nextStructural();
            if (separator == ',') {
                continue;
            }
            if (separator == '}') {
                break;
            }
            throw new StrictJsonException("expected ',' or '}' in object");
        }
        return new JsonValue.JsonObject(members);
    }

    private JsonValue parseArray(int depth) {
        expect('[');
        List<JsonValue> elements = new ArrayList<>();
        skipWhitespace();
        if (peekIs(']')) {
            position++;
            return new JsonValue.JsonArray(elements);
        }
        while (true) {
            elements.add(parseValue(depth + 1));
            skipWhitespace();
            char separator = nextStructural();
            if (separator == ',') {
                continue;
            }
            if (separator == ']') {
                break;
            }
            throw new StrictJsonException("expected ',' or ']' in array");
        }
        return new JsonValue.JsonArray(elements);
    }

    private String parseString() {
        expect('"');
        StringBuilder out = new StringBuilder();
        while (position < source.length()) {
            char character = source.charAt(position++);
            if (character == '"') {
                return out.toString();
            }
            if (character == '\\') {
                if (position >= source.length()) {
                    break;
                }
                char escape = source.charAt(position++);
                switch (escape) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (position + 4 > source.length()) {
                            throw new StrictJsonException("truncated unicode escape");
                        }
                        try {
                            out.append((char) Integer.parseInt(source.substring(position, position + 4), 16));
                        } catch (NumberFormatException bad) {
                            throw new StrictJsonException("bad unicode escape");
                        }
                        position += 4;
                    }
                    default -> throw new StrictJsonException("bad escape");
                }
            } else if (character < 0x20) {
                throw new StrictJsonException("raw control character in string");
            } else {
                out.append(character);
            }
        }
        throw new StrictJsonException("unterminated string");
    }

    private JsonValue parseNumber() {
        int start = position;
        if (peekIs('-')) {
            position++;
        }
        if (peekIs('0')) {
            position++;
        } else if (position < source.length() && source.charAt(position) >= '1' && source.charAt(position) <= '9') {
            while (position < source.length() && Character.isDigit(source.charAt(position))) {
                position++;
            }
        } else {
            throw new StrictJsonException("invalid number");
        }
        if (position < source.length()) {
            char character = source.charAt(position);
            if (character == '.' || character == 'e' || character == 'E') {
                throw new StrictJsonException("non-integer number rejected");
            }
        }
        try {
            return new JsonValue.JsonInteger(Long.parseLong(source.substring(start, position)));
        } catch (NumberFormatException overflow) {
            throw new StrictJsonException("integer out of range");
        }
    }

    private JsonValue parseBoolean() {
        if (source.startsWith("true", position)) {
            position += 4;
            return new JsonValue.JsonBoolean(true);
        }
        if (source.startsWith("false", position)) {
            position += 5;
            return new JsonValue.JsonBoolean(false);
        }
        throw new StrictJsonException("invalid literal");
    }

    private JsonValue parseNull() {
        if (source.startsWith("null", position)) {
            position += 4;
            return new JsonValue.JsonNull();
        }
        throw new StrictJsonException("invalid literal");
    }

    private char nextStructural() {
        if (position >= source.length()) {
            throw new StrictJsonException("unexpected end of input");
        }
        return source.charAt(position++);
    }

    private void skipWhitespace() {
        while (position < source.length()) {
            char character = source.charAt(position);
            if (character == ' ' || character == '\t' || character == '\n' || character == '\r') {
                position++;
            } else {
                break;
            }
        }
    }

    private boolean peekIs(char expected) {
        return position < source.length() && source.charAt(position) == expected;
    }

    private void expect(char expected) {
        if (!peekIs(expected)) {
            throw new StrictJsonException("expected '" + expected + "'");
        }
        position++;
    }
}
