package com.tfassbender.loreweave.watch.graph;

import com.tfassbender.loreweave.watch.domain.Backlink;
import com.tfassbender.loreweave.watch.domain.ValidationCategory;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

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
    void lookupByRawPathNormalizes() {
        Index index = builder.build(Path.of("src/test/resources/vault-valid").toAbsolutePath());
        // Case and .md-suffix variations both resolve via the convenience accessor.
        assertThat(index.getByPath("Characters/Kael.MD")).isPresent();
        assertThat(index.getByPath(Path.of("characters", "kael.md"))).isPresent();
    }
}
