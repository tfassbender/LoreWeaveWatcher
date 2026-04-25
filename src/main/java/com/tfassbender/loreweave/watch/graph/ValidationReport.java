package com.tfassbender.loreweave.watch.graph;

import com.tfassbender.loreweave.watch.domain.ValidationCategory;
import com.tfassbender.loreweave.watch.domain.ValidationIssue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Per-category counts of validation issues, with up to {@value #MAX_SAMPLES}
 * sample source paths retained for display in {@code /health}. Full counts are
 * preserved even when the sample list is truncated.
 */
public record ValidationReport(Map<ValidationCategory, CategoryStats> byCategory) {

    public static final int MAX_SAMPLES = 5;

    public ValidationReport {
        byCategory = byCategory == null ? Map.of() : Map.copyOf(byCategory);
    }

    public CategoryStats stats(ValidationCategory category) {
        return byCategory.getOrDefault(category, CategoryStats.EMPTY);
    }

    public int totalErrors() {
        int total = 0;
        for (var e : byCategory.entrySet()) {
            if (e.getKey().isError()) total += e.getValue().count();
        }
        return total;
    }

    public int totalWarnings() {
        int total = 0;
        for (var e : byCategory.entrySet()) {
            if (!e.getKey().isError()) total += e.getValue().count();
        }
        return total;
    }

    public record CategoryStats(int count, List<Path> samples) {
        public static final CategoryStats EMPTY = new CategoryStats(0, List.of());

        public CategoryStats {
            samples = samples == null ? List.of() : List.copyOf(samples);
        }
    }

    public static final class Builder {
        private final Map<ValidationCategory, List<Path>> samples = new EnumMap<>(ValidationCategory.class);
        private final Map<ValidationCategory, Integer> counts = new EnumMap<>(ValidationCategory.class);

        public Builder add(ValidationIssue issue) {
            if (issue == null) return this;
            ValidationCategory cat = issue.category();
            counts.merge(cat, 1, Integer::sum);
            samples.computeIfAbsent(cat, k -> new ArrayList<>());
            List<Path> s = samples.get(cat);
            if (s.size() < MAX_SAMPLES && issue.filePath() != null && !s.contains(issue.filePath())) {
                s.add(issue.filePath());
            }
            return this;
        }

        public Builder addAll(Iterable<ValidationIssue> issues) {
            if (issues != null) for (ValidationIssue i : issues) add(i);
            return this;
        }

        public ValidationReport build() {
            Map<ValidationCategory, CategoryStats> out = new EnumMap<>(ValidationCategory.class);
            for (var e : counts.entrySet()) {
                List<Path> s = samples.getOrDefault(e.getKey(), List.of());
                out.put(e.getKey(), new CategoryStats(e.getValue(), s));
            }
            return new ValidationReport(out);
        }
    }
}
