package com.tfassbender.loreweave.watch.server;

import java.util.List;
import java.util.Map;

/**
 * Minimal hand-rolled JSON renderer for the watcher's HTTP API. Supported
 * value types: {@code null}, {@link Boolean}, {@link Number}, {@link CharSequence},
 * {@link Map} (rendered as JSON object — pass a {@code LinkedHashMap} when key
 * order matters), and {@link List} (rendered as JSON array).
 *
 * <p>Strings are escaped per RFC 8259: backslash-quote, double-backslash, the
 * four mandatory control escapes (b/f/n/r/t), and any other code point below
 * {@code U+0020} as a six-character backslash-u escape. Non-ASCII code points
 * pass through verbatim — callers must write the result as UTF-8.
 */
public final class Json {

    private Json() {}

    public static String render(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, -1, 0);
        return sb.toString();
    }

    /** Renders with two-space indent so the on-disk config stays human-readable. */
    public static String renderPretty(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, 2, 0);
        sb.append('\n');
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object v, int indent, int depth) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof Boolean b) {
            sb.append(b);
        } else if (v instanceof Number n) {
            sb.append(n);
        } else if (v instanceof CharSequence s) {
            quote(sb, s.toString());
        } else if (v instanceof Map<?, ?> m) {
            writeObject(sb, m, indent, depth);
        } else if (v instanceof List<?> l) {
            writeArray(sb, l, indent, depth);
        } else {
            throw new IllegalArgumentException("unsupported JSON value type: " + v.getClass().getName());
        }
    }

    private static void writeObject(StringBuilder sb, Map<?, ?> m, int indent, int depth) {
        if (m.isEmpty()) { sb.append("{}"); return; }
        sb.append('{');
        boolean pretty = indent >= 0;
        boolean first = true;
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            if (pretty) { sb.append('\n'); pad(sb, indent * (depth + 1)); }
            quote(sb, String.valueOf(e.getKey()));
            sb.append(pretty ? ": " : ":");
            write(sb, e.getValue(), indent, depth + 1);
        }
        if (pretty) { sb.append('\n'); pad(sb, indent * depth); }
        sb.append('}');
    }

    private static void writeArray(StringBuilder sb, List<?> l, int indent, int depth) {
        if (l.isEmpty()) { sb.append("[]"); return; }
        sb.append('[');
        boolean pretty = indent >= 0;
        boolean first = true;
        for (Object item : l) {
            if (!first) sb.append(',');
            first = false;
            if (pretty) { sb.append('\n'); pad(sb, indent * (depth + 1)); }
            write(sb, item, indent, depth + 1);
        }
        if (pretty) { sb.append('\n'); pad(sb, indent * depth); }
        sb.append(']');
    }

    private static void pad(StringBuilder sb, int n) {
        for (int i = 0; i < n; i++) sb.append(' ');
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }
}
