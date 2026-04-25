package com.tfassbender.loreweave.watch.server;

import com.tfassbender.loreweave.watch.domain.ValidationCategory;
import com.tfassbender.loreweave.watch.domain.ValidationIssue;
import com.tfassbender.loreweave.watch.graph.Index;
import com.tfassbender.loreweave.watch.graph.IndexBuilder;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationApiTest {

    @Test
    void issueOrderIsSeverityCategoryPathMessage() {
        ValidationIssue warnB = ValidationIssue.warning(
                ValidationCategory.MISSING_TITLE, Path.of("z.md"), "warn-z");
        ValidationIssue errCatA = ValidationIssue.error(
                ValidationCategory.PARSE_ERRORS, Path.of("z.md"), "err-z");
        ValidationIssue errCatB = ValidationIssue.error(
                ValidationCategory.UNRESOLVED_LINKS, Path.of("a.md"), "err-a-1");
        ValidationIssue errCatBSamePath = ValidationIssue.error(
                ValidationCategory.UNRESOLVED_LINKS, Path.of("a.md"), "err-a-2");

        List<Map<String, Object>> sorted = ValidationApi.issues(List.of(
                warnB, errCatBSamePath, errCatA, errCatB));

        assertThat(sorted).extracting("category", "severity", "path", "message")
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple("parse_errors", "error", "z.md", "err-z"),
                        org.assertj.core.api.Assertions.tuple("unresolved_links", "error", "a.md", "err-a-1"),
                        org.assertj.core.api.Assertions.tuple("unresolved_links", "error", "a.md", "err-a-2"),
                        org.assertj.core.api.Assertions.tuple("missing_title", "warning", "z.md", "warn-z"));
    }

    @Test
    void normalizesBackslashesInPaths() {
        ValidationIssue i = ValidationIssue.error(
                ValidationCategory.PARSE_ERRORS, Path.of("dir", "note.md"), "msg");
        Map<String, Object> rendered = ValidationApi.issues(List.of(i)).get(0);
        assertThat((String) rendered.get("path")).doesNotContain("\\").contains("/");
    }

    @Test
    void rendersFullDocumentForFixtureVault() {
        Path vault = Path.of("src/test/resources/vault-invalid").toAbsolutePath();
        Index index = new IndexBuilder().build(vault);
        Instant when = Instant.parse("2026-04-25T12:00:00Z");

        String json = ValidationApi.render(index, when, vault);

        assertThat(json).startsWith("{\"vault\":");
        assertThat(json).contains("\"summary\":");
        assertThat(json).contains("\"errors\":");
        assertThat(json).contains("\"warnings\":");
        assertThat(json).contains("\"notes_served\":");
        assertThat(json).contains("\"notes_excluded\":");
        assertThat(json).contains("\"issues\":");
        assertThat(json).contains("\"scanned_at\":\"2026-04-25T12:00:00Z\"");
        assertThat(json).contains("\"severity\":\"error\"");
        // vault path uses forward slashes
        assertThat(json).doesNotContain("\\\\");
    }

    @Test
    void summaryCountsForValidVault() {
        Index index = new IndexBuilder().build(Path.of("src/test/resources/vault-valid").toAbsolutePath());
        Map<String, Object> summary = ValidationApi.summary(index);
        assertThat(summary.get("errors")).isEqualTo(0);
        assertThat(summary.get("warnings")).isEqualTo(0);
        assertThat((Integer) summary.get("notes_served")).isPositive();
        assertThat(summary.get("notes_excluded")).isEqualTo(0);
    }
}
