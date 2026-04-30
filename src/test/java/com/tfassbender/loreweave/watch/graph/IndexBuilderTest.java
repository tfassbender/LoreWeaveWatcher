package com.tfassbender.loreweave.watch.graph;

import com.tfassbender.loreweave.watch.domain.Backlink;
import com.tfassbender.loreweave.watch.domain.ValidationCategory;
import com.tfassbender.loreweave.watch.domain.ValidationIssue;
import com.tfassbender.loreweave.watch.parser.NoteAssembler;
import com.tfassbender.loreweave.watch.parser.ParseResult;
import com.tfassbender.loreweave.watch.parser.FrontmatterParser;
import com.tfassbender.loreweave.watch.parser.MarkdownBodyParser;
import com.tfassbender.loreweave.watch.parser.HashtagExtractor;
import com.tfassbender.loreweave.watch.parser.TitleResolver;
import com.tfassbender.loreweave.watch.parser.WikiLinkExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class IndexBuilderTest {

    private final IndexBuilder builder = new IndexBuilder();

    @Test
    void buildsValidFixtureVault() {
        Index index = builder.build(Path.of("src/test/resources/vault-valid").toAbsolutePath());

        assertThat(index.size()).isEqualTo(10);
        assertThat(index.notesByKey().keySet()).containsExactlyInAnyOrder(
                "characters/kael",
                "characters/rex",
                "characters/zara",
                "characters/tarek",
                "locations/karsis",
                "factions/union",
                "factions/inner-union",
                "events/border-incident",
                "events/karsis-siege",
                "items/void-crystal");

        IndexedNote kael = index.get("characters/kael").orElseThrow();
        assertThat(kael.resolvedLinks()).allMatch(ResolvedLink::isResolved);
        assertThat(kael.resolvedLinks())
                .extracting(rl -> rl.targetKey().orElse(""))
                .containsExactly("locations/karsis", "factions/union", "characters/rex");

        // Backlinks on kael — rex, zara, border-incident, karsis-siege, and void-crystal all point at him.
        assertThat(kael.backlinks())
                .extracting(Backlink::sourceKey)
                .containsExactlyInAnyOrder(
                        "characters/rex",
                        "characters/zara",
                        "events/border-incident",
                        "events/karsis-siege",
                        "items/void-crystal");

        IndexedNote union = index.get("factions/union").orElseThrow();
        assertThat(union.backlinks())
                .extracting(Backlink::sourceKey)
                .containsExactlyInAnyOrder("characters/kael", "characters/rex");

        // No errors, no warnings on the valid fixture.
        assertThat(index.report().totalErrors()).isZero();
        assertThat(index.report().totalWarnings()).isZero();
    }

    @Test
    void invalidFixtureProducesEveryValidationCategory() {
        Index index = builder.build(Path.of("src/test/resources/vault-invalid").toAbsolutePath());

        // Error categories — each remaining one fires at least once.
        assertThat(index.report().stats(ValidationCategory.PARSE_ERRORS).count()).isGreaterThanOrEqualTo(1);
        assertThat(index.report().stats(ValidationCategory.MISSING_REQUIRED_FIELDS).count()).isGreaterThanOrEqualTo(1);
        assertThat(index.report().stats(ValidationCategory.UNRESOLVED_LINKS).count()).isGreaterThanOrEqualTo(1);

        // Warning categories — each of the 'no_*' fixtures fires its category.
        assertThat(index.report().stats(ValidationCategory.MISSING_TITLE).count()).isGreaterThanOrEqualTo(1);
        assertThat(index.report().stats(ValidationCategory.MISSING_SUMMARY).count()).isGreaterThanOrEqualTo(1);
        assertThat(index.report().stats(ValidationCategory.MISSING_SCHEMA_VERSION).count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void invalidFixtureExcludesErroredNotesButServesWarningOnlyNotes() {
        Index index = builder.build(Path.of("src/test/resources/vault-invalid").toAbsolutePath());

        Set<String> servedKeys = index.notesByKey().keySet();

        // Error notes (missing_type.md, bad_yaml.md) must not appear.
        assertThat(servedKeys).doesNotContain("missing_type", "bad_yaml");
        // Warning-only notes remain. The unresolved-link note itself is still served.
        assertThat(servedKeys).contains("no_title", "no_summary", "no_schema", "unresolved");
    }

    @Test
    void duplicateUnresolvedLinkInSameNoteSurfacesTwice() {
        // Regression guard: the watcher UI's diff-aware renderer keys <li>s by
        // (severity, category, path, message). When a note mentions the same
        // broken [[target]] twice, IndexBuilder must report it twice — the UI
        // depends on that to exercise its duplicate-key bucketing. Collapsing
        // duplicates here would mask UI regressions like the row-growth bug.
        Index index = builder.build(Path.of("src/test/resources/vault-invalid").toAbsolutePath());

        long unresolvedDoesNotExistOnUnresolvedNote = index.issues().stream()
                .filter(i -> i.category() == ValidationCategory.UNRESOLVED_LINKS)
                .filter(i -> i.filePath().toString().replace('\\', '/').equals("unresolved.md"))
                .filter(i -> i.message().contains("[[DoesNotExist]]"))
                .count();
        assertThat(unresolvedDoesNotExistOnUnresolvedNote).isEqualTo(2);
    }

    @Test
    void sampleCapsAtFiveButCountKeepsGoing() {
        ValidationReport.Builder rb = new ValidationReport.Builder();
        for (int i = 0; i < 7; i++) {
            rb.add(com.tfassbender.loreweave.watch.domain.ValidationIssue.error(
                    ValidationCategory.PARSE_ERRORS,
                    Path.of("note-" + i + ".md"),
                    "bad"));
        }
        ValidationReport report = rb.build();
        assertThat(report.stats(ValidationCategory.PARSE_ERRORS).count()).isEqualTo(7);
        assertThat(report.stats(ValidationCategory.PARSE_ERRORS).samples()).hasSize(ValidationReport.MAX_SAMPLES);
    }

    @Test
    void assemblerCrashOnOneFileDoesNotKillTheScan(@TempDir Path vault) throws Exception {
        // Defense-in-depth: if the parser hits an unexpected runtime exception on
        // a single file, the rest of the vault must still be indexed and the
        // failure must surface as a PARSE_ERRORS issue (not propagate out and
        // crash the watch server / check command).
        Files.writeString(vault.resolve("ok.md"), """
                ---
                type: character
                title: Ok
                summary: ok
                schema_version: 1
                ---
                Body.
                """);
        Files.writeString(vault.resolve("boom.md"), "irrelevant — assembler will throw");

        NoteAssembler throwing = new NoteAssembler(
                new FrontmatterParser(), new MarkdownBodyParser(),
                new WikiLinkExtractor(), new HashtagExtractor(), new TitleResolver()) {
            @Override
            public ParseResult assemble(Path sourcePath, String rawContent) {
                if (sourcePath.getFileName().toString().equals("boom.md")) {
                    throw new IllegalStateException("synthetic parser blow-up");
                }
                return super.assemble(sourcePath, rawContent);
            }
        };
        IndexBuilder safeBuilder = new IndexBuilder(new VaultScanner(), throwing);

        Index index = safeBuilder.build(vault);

        assertThat(index.notesByKey().keySet()).contains("ok");
        assertThat(index.issues())
                .extracting(ValidationIssue::category, ValidationIssue::isError)
                .contains(tuple(ValidationCategory.PARSE_ERRORS, true));
        assertThat(index.issues())
                .filteredOn(i -> i.category() == ValidationCategory.PARSE_ERRORS)
                .extracting(i -> i.filePath().getFileName().toString())
                .contains("boom.md");
    }

    @Test
    void emptyTemplateFilesAreIndexedNotCrashed(@TempDir Path vault) throws Exception {
        // Reproduces the bug seen with test-vault/_templates/*.md — the YAML has
        // null-valued metadata (`metadata: { faction: , role: }`). Pre-fix this
        // tripped Map.copyOf in Note's compact constructor and crashed the scan.
        Files.createDirectories(vault.resolve("_templates"));
        Files.writeString(vault.resolve("_templates").resolve("character.md"), """
                ---
                type: character
                title:
                summary:
                schema_version: 1
                aliases: []
                metadata:
                  faction:
                  role:
                ---
                Short narrative description.

                #character
                """);

        Index index = builder.build(vault);

        assertThat(index.notesByKey()).containsKey("_templates/character");
        // Empty title/summary surface as warnings — the file is still served.
        assertThat(index.issues())
                .extracting(ValidationIssue::category)
                .contains(ValidationCategory.MISSING_TITLE, ValidationCategory.MISSING_SUMMARY);
    }

    @Test
    void lookupByRawPathNormalizes() {
        Index index = builder.build(Path.of("src/test/resources/vault-valid").toAbsolutePath());
        // Case and .md-suffix variations both resolve via the convenience accessor.
        assertThat(index.getByPath("Characters/Kael.MD")).isPresent();
        assertThat(index.getByPath(Path.of("characters", "kael.md"))).isPresent();
    }
}
