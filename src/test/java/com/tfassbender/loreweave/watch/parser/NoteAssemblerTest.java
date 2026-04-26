package com.tfassbender.loreweave.watch.parser;

import com.tfassbender.loreweave.watch.domain.Link;
import com.tfassbender.loreweave.watch.domain.ValidationCategory;
import com.tfassbender.loreweave.watch.domain.ValidationIssue;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class NoteAssemblerTest {

    private final NoteAssembler assembler = new NoteAssembler();
    private final Path file = Path.of("characters", "kael.md");

    @Test
    void assemblesHappyPath() {
        String raw = """
                ---
                type: character
                title: Kael Varyn
                summary: Outer Union scout.
                schema_version: 1
                aliases: [Kael, The Scout]
                metadata:
                  faction: outer_union
                ---

                Kael was stationed at [[locations/karsis]]. #pov
                Loyal to the [[union|Union]]. #major
                """;
        ParseResult result = assembler.assemble(file, raw);
        assertThat(result).isInstanceOf(ParseResult.Success.class);
        var success = (ParseResult.Success) result;
        assertThat(success.issues()).isEmpty();
        assertThat(success.note().type()).isEqualTo("character");
        assertThat(success.note().title()).isEqualTo("Kael Varyn");
        assertThat(success.note().schemaVersion()).isEqualTo(1);
        assertThat(success.note().aliases()).containsExactly("Kael", "The Scout");
        assertThat(success.note().metadata()).containsEntry("faction", "outer_union");
        assertThat(success.note().tags()).containsExactly("pov", "major");
        assertThat(success.note().links()).extracting(Link::rawTarget)
                .containsExactly("locations/karsis", "union");
        assertThat(success.note().sourcePath()).isEqualTo(file);
        assertThat(success.note().key()).isEqualTo("characters/kael");
    }

    @Test
    void missingTypeFails() {
        String raw = """
                ---
                title: Orphan
                ---

                Body.
                """;
        ParseResult result = assembler.assemble(file, raw);
        assertThat(result).isInstanceOf(ParseResult.Failure.class);
        assertThat(result.issues())
                .extracting(ValidationIssue::category)
                .contains(ValidationCategory.MISSING_REQUIRED_FIELDS);
    }

    @Test
    void missingRecommendedFieldsSurfaceAsWarningsNotErrors() {
        String raw = """
                ---
                type: character
                ---

                Body.
                """;
        ParseResult result = assembler.assemble(file, raw);
        assertThat(result).isInstanceOf(ParseResult.Success.class);
        assertThat(result.issues())
                .extracting(ValidationIssue::category)
                .containsExactlyInAnyOrder(
                        ValidationCategory.MISSING_TITLE,
                        ValidationCategory.MISSING_SUMMARY,
                        ValidationCategory.MISSING_SCHEMA_VERSION);
        var success = (ParseResult.Success) result;
        // Title falls back to the filename without .md.
        assertThat(success.note().title()).isEqualTo("kael");
        assertThat(success.note().schemaVersion()).isEqualTo(1);
    }

    @Test
    void malformedYamlIsParseError() {
        String raw = """
                ---
                type: [unterminated
                ---

                Body.
                """;
        ParseResult result = assembler.assemble(file, raw);
        assertThat(result).isInstanceOf(ParseResult.Failure.class);
        assertThat(result.issues())
                .extracting(ValidationIssue::category)
                .contains(ValidationCategory.PARSE_ERRORS);
    }

    @Test
    void emptyTemplateWithNullValuedMetadataDoesNotCrash() {
        // Mirrors test-vault/_templates/character.md: every field is present but
        // empty, so YAML loads them as null. Map.copyOf would NPE here — the
        // assembler must instead produce a Success (or warning-bearing result)
        // so the scan can keep going and the issue surfaces in the UI.
        String raw = """
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
                """;
        ParseResult result = assembler.assemble(file, raw);
        assertThat(result).isInstanceOf(ParseResult.Success.class);
        var success = (ParseResult.Success) result;
        // Empty title and summary surface as warnings, not errors.
        assertThat(success.issues()).extracting(ValidationIssue::category)
                .contains(ValidationCategory.MISSING_TITLE, ValidationCategory.MISSING_SUMMARY);
        // Null-valued metadata entries are preserved (null is a valid YAML value).
        assertThat(success.note().metadata()).containsKeys("faction", "role");
        assertThat(success.note().metadata().get("faction")).isNull();
        assertThat(success.note().metadata().get("role")).isNull();
    }

    @Test
    void aliasesListWithNullEntryDoesNotCrash() {
        // YAML `aliases: [, Foo]` yields a list whose first element is null.
        // List.copyOf would NPE; the assembler must tolerate it.
        String raw = """
                ---
                type: character
                title: K
                summary: s
                schema_version: 1
                aliases:
                  -
                  - Foo
                ---
                Body.
                """;
        ParseResult result = assembler.assemble(file, raw);
        assertThat(result).isInstanceOf(ParseResult.Success.class);
    }

    @Test
    void keyIsDerivedFromSourcePathAndPortableAcrossSeparators() {
        String raw = """
                ---
                type: character
                title: K
                summary: s
                schema_version: 1
                ---
                """;
        ParseResult winRes = assembler.assemble(Path.of("characters", "kael.md"), raw);
        assertThat(winRes).isInstanceOf(ParseResult.Success.class);
        assertThat(((ParseResult.Success) winRes).note().key()).isEqualTo("characters/kael");
    }
}
