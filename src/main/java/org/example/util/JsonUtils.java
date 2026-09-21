package org.example.util;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsonUtils {
    private JsonUtils() {
    }

    public static String stringify(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String stringValue) {
            return "\"" + escape(stringValue) + "\"";
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof BigDecimal) {
            return String.valueOf(value);
        }
        if (value instanceof Map<?, ?> mapValue) {
            StringBuilder builder = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                if (!first) {
                    builder.append(',');
                }
                builder.append(stringify(String.valueOf(entry.getKey())));
                builder.append(':');
                builder.append(stringify(entry.getValue()));
                first = false;
            }
            builder.append('}');
            return builder.toString();
        }
        if (value instanceof Iterable<?> iterable) {
            StringBuilder builder = new StringBuilder("[");
            boolean first = true;
            for (Object item : iterable) {
                if (!first) {
                    builder.append(',');
                }
                builder.append(stringify(item));
                first = false;
            }
            builder.append(']');
            return builder.toString();
        }
        return stringify(String.valueOf(value));
    }

    public static Map<String, Object> parseObject(String json) {
        Object parsed = new Parser(json).parseValue();
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new AppException(400, "Request body must be a JSON object.");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static final class Parser {
        private final String json;
        private int index;

        private Parser(String json) {
            this.json = json == null ? "" : json.trim();
        }

        private Object parseValue() {
            skipWhitespace();
            if (index >= json.length()) {
                throw new AppException(400, "JSON body is empty.");
            }
            char current = json.charAt(index);
            if (current == '{') {
                return parseObject();
            }
            if (current == '[') {
                return parseArray();
            }
            if (current == '"') {
                return parseString();
            }
            if (current == 't' || current == 'f') {
                return parseBoolean();
            }
            if (current == 'n') {
                return parseNull();
            }
            return parseNumber();
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            expect('{');
            skipWhitespace();
            if (peek('}')) {
                expect('}');
                return map;
            }
            while (true) {
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                map.put(key, value);
                skipWhitespace();
                if (peek('}')) {
                    expect('}');
                    return map;
                }
                expect(',');
            }
        }

        private List<Object> parseArray() {
            List<Object> items = new ArrayList<>();
            expect('[');
            skipWhitespace();
            if (peek(']')) {
                expect(']');
                return items;
            }
            while (true) {
                items.add(parseValue());
                skipWhitespace();
                if (peek(']')) {
                    expect(']');
                    return items;
                }
                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder builder = new StringBuilder();
            while (index < json.length()) {
                char current = json.charAt(index++);
                if (current == '"') {
                    return builder.toString();
                }
                if (current == '\\') {
                    if (index >= json.length()) {
                        throw new AppException(400, "Invalid escape sequence.");
                    }
                    char escaped = json.charAt(index++);
                    switch (escaped) {
                        case '"':
                        case '\\':
                        case '/':
                            builder.append(escaped);
                            break;
                        case 'b':
                            builder.append('\b');
                            break;
                        case 'f':
                            builder.append('\f');
                            break;
                        case 'n':
                            builder.append('\n');
                            break;
                        case 'r':
                            builder.append('\r');
                            break;
                        case 't':
                            builder.append('\t');
                            break;
                        case 'u':
                            builder.append(parseUnicode());
                            break;
                        default:
                            throw new AppException(400, "Unsupported escape sequence.");
                    }
                } else {
                    builder.append(current);
                }
            }
            throw new AppException(400, "Unterminated JSON string.");
        }

        private char parseUnicode() {
            if (index + 4 > json.length()) {
                throw new AppException(400, "Invalid unicode escape.");
            }
            String hex = json.substring(index, index + 4);
            index += 4;
            return (char) Integer.parseInt(hex, 16);
        }

        private Boolean parseBoolean() {
            if (json.startsWith("true", index)) {
                index += 4;
                return true;
            }
            if (json.startsWith("false", index)) {
                index += 5;
                return false;
            }
            throw new AppException(400, "Invalid boolean value.");
        }

        private Object parseNull() {
            if (json.startsWith("null", index)) {
                index += 4;
                return null;
            }
            throw new AppException(400, "Invalid null value.");
        }

        private BigDecimal parseNumber() {
            int start = index;
            while (index < json.length()) {
                char current = json.charAt(index);
                if ((current >= '0' && current <= '9') || current == '-' || current == '+' || current == '.' || current == 'e' || current == 'E') {
                    index++;
                    continue;
                }
                break;
            }
            String number = json.substring(start, index);
            if (number.isBlank()) {
                throw new AppException(400, "Invalid number value.");
            }
            try {
                return new BigDecimal(number);
            } catch (NumberFormatException ex) {
                throw new AppException(400, "Invalid number value.");
            }
        }

        private void expect(char target) {
            skipWhitespace();
            if (index >= json.length() || json.charAt(index) != target) {
                throw new AppException(400, "Invalid JSON format.");
            }
            index++;
        }

        private boolean peek(char target) {
            skipWhitespace();
            return index < json.length() && json.charAt(index) == target;
        }

        private void skipWhitespace() {
            while (index < json.length() && Character.isWhitespace(json.charAt(index))) {
                index++;
            }
        }
    }
}
