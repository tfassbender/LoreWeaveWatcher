package com.tfassbender.loreweave.watch.server;

import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

import java.util.List;
import java.util.Map;

/**
 * Tiny convenience wrapper around snakeyaml-engine for parsing JSON input.
 * snakeyaml-engine speaks YAML 1.2, of which JSON is a strict subset, so it
 * happily round-trips the small flat config documents the watcher persists.
 *
 * <p>Returns the same shapes the rest of the watcher expects:
 * {@link Map} for objects, {@link List} for arrays, {@link Boolean}/{@link Number}/{@link String}
 * for scalars. Unknown keys are surfaced verbatim — callers are responsible for
 * coercion and validation.
 */
public final class JsonReader {

    private JsonReader() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        if (text == null || text.isBlank()) return Map.of();
        Object parsed = parse(text);
        if (parsed == null) return Map.of();
        if (!(parsed instanceof Map)) {
            throw new IllegalArgumentException(
                    "expected JSON object at top level, got " + parsed.getClass().getSimpleName());
        }
        return (Map<String, Object>) parsed;
    }

    public static Object parse(String text) {
        Load load = new Load(LoadSettings.builder().build());
        return load.loadFromString(text == null ? "" : text);
    }

    public static int intOr(Map<String, Object> m, String key, int def) {
        Object v = m.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    public static long longOr(Map<String, Object> m, String key, long def) {
        Object v = m.get(key);
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) {
            try { return Long.parseLong(s.trim()); } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    public static boolean boolOr(Map<String, Object> m, String key, boolean def) {
        Object v = m.get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) {
            String t = s.trim().toLowerCase();
            if (t.equals("true")) return true;
            if (t.equals("false")) return false;
        }
        return def;
    }

    public static String stringOr(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        if (v == null) return def;
        return v.toString();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> objectOr(Map<String, Object> m, String key, Map<String, Object> def) {
        Object v = m.get(key);
        if (v instanceof Map<?, ?>) return (Map<String, Object>) v;
        return def;
    }

    public static List<String> stringListOr(Map<String, Object> m, String key, List<String> def) {
        Object v = m.get(key);
        if (!(v instanceof List<?> list)) return def;
        List<String> out = new java.util.ArrayList<>(list.size());
        for (Object item : list) {
            if (item != null) out.add(item.toString());
        }
        return out;
    }
}
