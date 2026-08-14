package com.acclash.vmcomputers.emu;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader/writer, just enough for QMP.
 *
 * <p>QMP is line-delimited JSON and its messages are shallow, so rather than depend on Gson
 * happening to be on the server classpath this package stays pure-JDK. That keeps the whole
 * emulator layer compilable and runnable without a Minecraft server, which is the main reason it
 * exists as a separate package.
 *
 * <p>Values map to: {@code Map<String, Object>}, {@code List<Object>}, {@link String},
 * {@link Double}, {@link Boolean}, and {@code null}.
 */
public final class Json {

    private Json() {
    }

    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.skipWhitespace();
        Object v = p.readValue();
        p.skipWhitespace();
        if (!p.atEnd()) {
            throw new IllegalArgumentException("trailing content at offset " + p.pos);
        }
        return v;
    }

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value);
        return sb.toString();
    }

    /** Convenience for building small argument objects. */
    public static Map<String, Object> map(Object... keyValuePairs) {
        if (keyValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("expected an even number of arguments");
        }
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            m.put(String.valueOf(keyValuePairs[i]), keyValuePairs[i + 1]);
        }
        return m;
    }

    // ---- typed accessors -------------------------------------------------

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object v) {
        if (v == null) {
            return null;
        }
        if (!(v instanceof Map)) {
            throw new IllegalArgumentException("expected object, got " + typeName(v));
        }
        return (Map<String, Object>) v;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asArray(Object v) {
        if (v == null) {
            return null;
        }
        if (!(v instanceof List)) {
            throw new IllegalArgumentException("expected array, got " + typeName(v));
        }
        return (List<Object>) v;
    }

    public static Map<String, Object> getObject(Map<String, Object> o, String key) {
        return o == null ? null : asObject(o.get(key));
    }

    public static String getString(Map<String, Object> o, String key, String fallback) {
        Object v = o == null ? null : o.get(key);
        return v instanceof String ? (String) v : fallback;
    }

    public static long getLong(Map<String, Object> o, String key, long fallback) {
        Object v = o == null ? null : o.get(key);
        return v instanceof Number ? ((Number) v).longValue() : fallback;
    }

    public static boolean getBoolean(Map<String, Object> o, String key, boolean fallback) {
        Object v = o == null ? null : o.get(key);
        return v instanceof Boolean ? ((Boolean) v).booleanValue() : fallback;
    }

    private static String typeName(Object v) {
        return v == null ? "null" : v.getClass().getSimpleName();
    }

    // ---- writing --------------------------------------------------------

    private static void writeValue(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String) {
            writeString(sb, (String) v);
        } else if (v instanceof Boolean) {
            sb.append(((Boolean) v).booleanValue() ? "true" : "false");
        } else if (v instanceof Number) {
            writeNumber(sb, (Number) v);
        } else if (v instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeString(sb, String.valueOf(e.getKey()));
                sb.append(':');
                writeValue(sb, e.getValue());
            }
            sb.append('}');
        } else if (v instanceof Iterable) {
            sb.append('[');
            boolean first = true;
            for (Object e : (Iterable<?>) v) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeValue(sb, e);
            }
            sb.append(']');
        } else {
            throw new IllegalArgumentException("cannot serialize " + typeName(v));
        }
    }

    private static void writeNumber(StringBuilder sb, Number n) {
        if (n instanceof Double || n instanceof Float) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new IllegalArgumentException("cannot serialize " + d);
            }
            // Emit integral doubles without the ".0" so QMP sees proper integers.
            if (d == Math.rint(d) && Math.abs(d) < 9.007199254740992E15) {
                sb.append((long) d);
            } else {
                sb.append(d);
            }
        } else {
            sb.append(n.longValue());
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    // ---- parsing --------------------------------------------------------

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        boolean atEnd() {
            return pos >= s.length();
        }

        void skipWhitespace() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        private char peek() {
            if (pos >= s.length()) {
                throw new IllegalArgumentException("unexpected end of input");
            }
            return s.charAt(pos);
        }

        private void expect(char c) {
            if (pos >= s.length() || s.charAt(pos) != c) {
                throw new IllegalArgumentException("expected '" + c + "' at offset " + pos);
            }
            pos++;
        }

        Object readValue() {
            skipWhitespace();
            char c = peek();
            switch (c) {
                case '{':
                    return readObject();
                case '[':
                    return readArray();
                case '"':
                    return readString();
                case 't':
                    expectLiteral("true");
                    return Boolean.TRUE;
                case 'f':
                    expectLiteral("false");
                    return Boolean.FALSE;
                case 'n':
                    expectLiteral("null");
                    return null;
                default:
                    return readNumber();
            }
        }

        private void expectLiteral(String lit) {
            if (!s.startsWith(lit, pos)) {
                throw new IllegalArgumentException("expected '" + lit + "' at offset " + pos);
            }
            pos += lit.length();
        }

        private Map<String, Object> readObject() {
            expect('{');
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return m;
            }
            while (true) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                expect(':');
                m.put(key, readValue());
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    pos++;
                } else if (c == '}') {
                    pos++;
                    return m;
                } else {
                    throw new IllegalArgumentException("expected ',' or '}' at offset " + pos);
                }
            }
        }

        private List<Object> readArray() {
            expect('[');
            List<Object> list = new ArrayList<Object>();
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(readValue());
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    pos++;
                } else if (c == ']') {
                    pos++;
                    return list;
                } else {
                    throw new IllegalArgumentException("expected ',' or ']' at offset " + pos);
                }
            }
        }

        private String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (pos >= s.length()) {
                    throw new IllegalArgumentException("unterminated string");
                }
                char c = s.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                char esc = s.charAt(pos++);
                switch (esc) {
                    case '"':
                        sb.append('"');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    case '/':
                        sb.append('/');
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'u':
                        sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                        pos += 4;
                        break;
                    default:
                        throw new IllegalArgumentException("bad escape '\\" + esc + "'");
                }
            }
        }

        private Object readNumber() {
            int start = pos;
            if (pos < s.length() && (s.charAt(pos) == '-' || s.charAt(pos) == '+')) {
                pos++;
            }
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '-' || c == '+') {
                    pos++;
                } else {
                    break;
                }
            }
            if (start == pos) {
                throw new IllegalArgumentException("expected a value at offset " + pos);
            }
            return Double.valueOf(Double.parseDouble(s.substring(start, pos)));
        }
    }
}
