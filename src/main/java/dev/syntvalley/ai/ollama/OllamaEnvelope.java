package dev.syntvalley.ai.ollama;

/**
 * The two fields the adapter needs from a non-streaming Ollama {@code /api/generate} reply: the
 * {@code done} flag and the {@code response} text. Parsed by a tiny, dependency-free scanner (so the
 * backend needs no JSON library on the mod's compile classpath) that correctly skips nested values and
 * string escapes — a {@code "done"} or {@code "response"} substring inside the model's own text can
 * never be mistaken for a top-level key.
 */
public record OllamaEnvelope(boolean done, String response) {

    /** Parses a top-level JSON object; throws {@link IllegalArgumentException} on anything malformed. */
    public static OllamaEnvelope parse(String json) {
        Scanner scanner = new Scanner(json);
        scanner.skipWhitespace();
        scanner.expect('{');

        Boolean done = null;
        String response = null;
        scanner.skipWhitespace();
        if (!scanner.peekIs('}')) {
            while (true) {
                scanner.skipWhitespace();
                String key = scanner.readString();
                scanner.skipWhitespace();
                scanner.expect(':');
                scanner.skipWhitespace();
                switch (key) {
                    case "done" -> done = scanner.readBoolean();
                    case "response" -> response = scanner.readString();
                    default -> scanner.skipValue();
                }
                scanner.skipWhitespace();
                if (scanner.peekIs(',')) {
                    scanner.next();
                } else {
                    break;
                }
            }
        }
        scanner.skipWhitespace();
        scanner.expect('}');

        if (done == null || response == null) {
            throw new IllegalArgumentException("missing done or response field");
        }
        return new OllamaEnvelope(done, response);
    }

    /** Escapes a string into a JSON string literal, including surrounding quotes. */
    public static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2);
        out.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (character < 0x20) {
                        out.append(String.format("\\u%04x", (int) character));
                    } else {
                        out.append(character);
                    }
                }
            }
        }
        out.append('"');
        return out.toString();
    }

    /** Minimal, strict JSON scanner: enough to walk a top-level object and skip arbitrary values. */
    private static final class Scanner {
        private final String source;
        private int position;

        Scanner(String source) {
            this.source = source;
        }

        void skipWhitespace() {
            while (position < source.length() && Character.isWhitespace(source.charAt(position))) {
                position++;
            }
        }

        boolean peekIs(char expected) {
            return position < source.length() && source.charAt(position) == expected;
        }

        void next() {
            position++;
        }

        void expect(char expected) {
            if (!peekIs(expected)) {
                throw new IllegalArgumentException("expected '" + expected + "' at " + position);
            }
            position++;
        }

        String readString() {
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
                        case 'n' -> out.append('\n');
                        case 'r' -> out.append('\r');
                        case 't' -> out.append('\t');
                        case 'b' -> out.append('\b');
                        case 'f' -> out.append('\f');
                        case 'u' -> {
                            if (position + 4 > source.length()) {
                                throw new IllegalArgumentException("truncated unicode escape");
                            }
                            out.append((char) Integer.parseInt(source.substring(position, position + 4), 16));
                            position += 4;
                        }
                        default -> throw new IllegalArgumentException("bad escape \\" + escape);
                    }
                } else {
                    out.append(character);
                }
            }
            throw new IllegalArgumentException("unterminated string");
        }

        boolean readBoolean() {
            if (source.startsWith("true", position)) {
                position += 4;
                return true;
            }
            if (source.startsWith("false", position)) {
                position += 5;
                return false;
            }
            throw new IllegalArgumentException("expected boolean at " + position);
        }

        void skipValue() {
            if (position >= source.length()) {
                throw new IllegalArgumentException("unexpected end of value");
            }
            char character = source.charAt(position);
            switch (character) {
                case '"' -> readString();
                case '{' -> skipContainer('{', '}');
                case '[' -> skipContainer('[', ']');
                default -> skipScalar();
            }
        }

        private void skipContainer(char open, char close) {
            expect(open);
            int depth = 1;
            while (position < source.length() && depth > 0) {
                char character = source.charAt(position);
                if (character == '"') {
                    readString();
                    continue;
                }
                if (character == open) {
                    depth++;
                } else if (character == close) {
                    depth--;
                }
                position++;
            }
            if (depth != 0) {
                throw new IllegalArgumentException("unterminated container");
            }
        }

        private void skipScalar() {
            int start = position;
            while (position < source.length()) {
                char character = source.charAt(position);
                if (character == ',' || character == '}' || character == ']' || Character.isWhitespace(character)) {
                    break;
                }
                position++;
            }
            if (position == start) {
                throw new IllegalArgumentException("empty scalar at " + position);
            }
        }
    }
}
