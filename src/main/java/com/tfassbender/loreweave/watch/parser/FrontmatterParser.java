package com.tfassbender.loreweave.watch.parser;

import com.tfassbender.loreweave.watch.domain.ValidationCategory;
import com.tfassbender.loreweave.watch.domain.ValidationIssue;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Splits a raw markdown file into its YAML frontmatter and body, and parses the
 * YAML into a loose {@code Map<String, Object>}. Malformed YAML surfaces as a
 * {@link ValidationCategory#PARSE_ERRORS} issue rather than a thrown exception,
 * so a single bad file doesn't crash a vault scan.
 */
public final class FrontmatterParser {

    private static final String DELIMITER = "---";

    private final Load yaml;

    public FrontmatterParser() {
        this.yaml = new Load(LoadSettings.builder().build());
    }

    public Split split(String raw) {
        if (raw == null || raw.isEmpty()) {
            return new Split("", raw == null ? "" : raw);
        }
        // Accept an optional leading BOM or whitespace-only lines before the first ---.
        String trimmedLeading = stripLeadingBlankLines(raw);
        if (!trimmedLeading.startsWith(DELIMITER)) {
            return new Split("", raw);
        }
        int firstLineEnd = trimmedLeading.indexOf('\n');
        if (firstLineEnd < 0) {
            return new Split("", raw);
        }
        // Find the closing ---, which must sit on its own line.
        int searchFrom = firstLineEnd + 1;
        int close = findClosingDelimiter(trimmedLeading, searchFrom);
        if (close < 0) {
            return new Split("", raw);
        }
        String yamlText = trimmedLeading.substring(searchFrom, close);
        int afterClose = trimmedLeading.indexOf('\n', close);
        String body = afterClose < 0 ? "" : trimmedLeading.substring(afterClose + 1);
        return new Split(yamlText, body);
    }

    public Parsed parse(String yamlText, Path filePath) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (yamlText == null || yamlText.isBlank()) {
            return new Parsed(Map.of(), issues);
        }
        try {
            Object loaded = yaml.loadFromString(yamlText);
            if (loaded == null) {
                return new Parsed(Map.of(), issues);
            }
            if (!(loaded instanceof Map<?, ?> map)) {
                issues.add(ValidationIssue.error(
                        ValidationCategory.PARSE_ERRORS, filePath,
                        "Frontmatter must be a YAML mapping, was " + loaded.getClass().getSimpleName()));
                return new Parsed(Map.of(), issues);
            }
            Map<String, Object> typed = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                typed.put(String.valueOf(e.getKey()), e.getValue());
            }
            return new Parsed(typed, issues);
        } catch (YamlEngineException ex) {
            issues.add(ValidationIssue.error(
                    ValidationCategory.PARSE_ERRORS, filePath,
                    "YAML parse failed: " + ex.getMessage()));
            return new Parsed(Map.of(), issues);
        }
    }

    private static String stripLeadingBlankLines(String raw) {
        int i = 0;
        while (i < raw.length()) {
            char c = raw.charAt(i);
            if (c == '\uFEFF' || c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                i++;
            } else {
                break;
            }
        }
        return raw.substring(i);
    }

    private static int findClosingDelimiter(String text, int from) {
        int idx = from;
        while (idx < text.length()) {
            int lineEnd = text.indexOf('\n', idx);
            String line = lineEnd < 0 ? text.substring(idx) : text.substring(idx, lineEnd);
            String stripped = line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
            if (stripped.equals(DELIMITER)) {
                return idx;
            }
            if (lineEnd < 0) {
                return -1;
            }
            idx = lineEnd + 1;
        }
        return -1;
    }

    public record Split(String yamlText, String body) {}

    public record Parsed(Map<String, Object> fields, List<ValidationIssue> issues) {}
}
