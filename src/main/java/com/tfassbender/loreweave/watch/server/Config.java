package com.tfassbender.loreweave.watch.server;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Persisted dashboard preferences. Lives next to the jar at
 * {@code <vault>/.loreweave/lore-weave-watch.json}. Hot-reloaded on every
 * {@code POST /api/config} (port is intentionally absent — bind port stays a
 * CLI concern).
 *
 * <p>Validation rule worth keeping in mind: the idle-shutdown threshold must
 * give the browser at least one full poll cycle plus 5 s of slack to avoid the
 * server killing itself between two healthy polls.
 */
public record Config(
        String theme,
        boolean autoOpenBrowser,
        int pollIntervalMs,
        IdleShutdownConfig idleShutdown,
        List<String> ignorePaths) {

    public static final long IDLE_MIN_HEADROOM_MS = 5_000L;

    public Config {
        theme = (theme == null) ? "dark" : theme.toLowerCase(Locale.ROOT);
        if (!theme.equals("dark") && !theme.equals("light")) theme = "dark";
        if (pollIntervalMs < 200) pollIntervalMs = 200;
        if (idleShutdown == null) idleShutdown = IdleShutdownConfig.DEFAULTS;
        ignorePaths = ignorePaths == null ? List.of() : List.copyOf(ignorePaths);
    }

    public record IdleShutdownConfig(boolean enabled, long thresholdMs, long graceMs) {
        public static final IdleShutdownConfig DEFAULTS = new IdleShutdownConfig(true, 10_000, 30_000);

        public IdleShutdownConfig {
            if (thresholdMs < 1_000) thresholdMs = 1_000;
            if (graceMs < 0) graceMs = 0;
        }
    }

    public static Config defaults() {
        return new Config("dark", true, 1_000, IdleShutdownConfig.DEFAULTS, List.of());
    }

    /**
     * Validates the cross-field constraint: idle threshold must be at least
     * {@link #IDLE_MIN_HEADROOM_MS} above the poll interval. Returns a human
     * error message, or null if the config is consistent. The check is skipped
     * when idle shutdown is disabled.
     */
    public String validationError() {
        if (!idleShutdown.enabled()) return null;
        long minThreshold = pollIntervalMs + IDLE_MIN_HEADROOM_MS;
        if (idleShutdown.thresholdMs() < minThreshold) {
            return "idle_shutdown.threshold_ms must be at least poll_interval_ms + "
                    + IDLE_MIN_HEADROOM_MS + " (got " + idleShutdown.thresholdMs()
                    + ", minimum " + minThreshold + ")";
        }
        return null;
    }

    /** Render to the on-disk JSON shape (snake_case, nested idle_shutdown). */
    public Map<String, Object> toJson() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("theme", theme);
        root.put("auto_open_browser", autoOpenBrowser);
        root.put("poll_interval_ms", pollIntervalMs);
        Map<String, Object> idle = new LinkedHashMap<>();
        idle.put("enabled", idleShutdown.enabled());
        idle.put("threshold_ms", idleShutdown.thresholdMs());
        idle.put("grace_ms", idleShutdown.graceMs());
        root.put("idle_shutdown", idle);
        root.put("ignore_paths", ignorePaths);
        return root;
    }

    public static Config fromJson(Map<String, Object> json) {
        Map<String, Object> idleMap = JsonReader.objectOr(json, "idle_shutdown", Map.of());
        IdleShutdownConfig idle = new IdleShutdownConfig(
                JsonReader.boolOr(idleMap, "enabled", IdleShutdownConfig.DEFAULTS.enabled()),
                JsonReader.longOr(idleMap, "threshold_ms", IdleShutdownConfig.DEFAULTS.thresholdMs()),
                JsonReader.longOr(idleMap, "grace_ms", IdleShutdownConfig.DEFAULTS.graceMs()));
        return new Config(
                JsonReader.stringOr(json, "theme", "dark"),
                JsonReader.boolOr(json, "auto_open_browser", true),
                JsonReader.intOr(json, "poll_interval_ms", 1_000),
                idle,
                JsonReader.stringListOr(json, "ignore_paths", List.of()));
    }
}
