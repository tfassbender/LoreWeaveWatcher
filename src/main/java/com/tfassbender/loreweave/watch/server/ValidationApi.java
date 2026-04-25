package com.tfassbender.loreweave.watch.server;

import com.tfassbender.loreweave.watch.domain.ValidationCategory;
import com.tfassbender.loreweave.watch.domain.ValidationIssue;
import com.tfassbender.loreweave.watch.graph.Index;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders an {@link Index} into the JSON shape served by {@code /api/validation}.
 * Issue order is fixed (severity desc, category asc, path asc, message asc) so
 * the dashboard can do diff-aware updates without reordering rows on every
 * poll.
 */
public final class ValidationApi {

    private ValidationApi() {}

    public static String render(Index index, Instant scannedAt) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("summary", summary(index));
        root.put("issues", issues(index.issues()));
        root.put("scanned_at", scannedAt.toString());
        return Json.render(root);
    }

    static Map<String, Object> summary(Index index) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("errors", index.report().totalErrors());
        s.put("warnings", index.report().totalWarnings());
        s.put("notes_served", index.size());
        s.put("notes_excluded", index.notesExcluded());
        return s;
    }

    static List<Map<String, Object>> issues(List<ValidationIssue> raw) {
        List<ValidationIssue> sorted = new ArrayList<>(raw);
        sorted.sort(ISSUE_ORDER);
        List<Map<String, Object>> out = new ArrayList<>(sorted.size());
        for (ValidationIssue i : sorted) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("category", categoryName(i.category()));
            e.put("severity", i.isError() ? "error" : "warning");
            e.put("path", normalizePath(i.filePath()));
            e.put("message", i.message());
            out.add(e);
        }
        return out;
    }

    static String categoryName(ValidationCategory c) {
        return c.name().toLowerCase(Locale.ROOT);
    }

    private static String normalizePath(Path p) {
        if (p == null) return "";
        return p.toString().replace('\\', '/');
    }

    /**
     * (severity desc, category asc, path asc, message asc). Errors sort before
     * warnings; ties broken by category name, then path, then message.
     */
    static final Comparator<ValidationIssue> ISSUE_ORDER = Comparator
            .<ValidationIssue, Integer>comparing(i -> i.isError() ? 0 : 1)
            .thenComparing(i -> categoryName(i.category()))
            .thenComparing(i -> normalizePath(i.filePath()))
            .thenComparing(ValidationIssue::message);
}
