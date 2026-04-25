package com.tfassbender.loreweave.watch.parser;

import com.tfassbender.loreweave.watch.domain.ValidationCategory;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FrontmatterParserTest {

    private final FrontmatterParser parser = new FrontmatterParser();
    private final Path file = Path.of("note.md");

    @Test
    void splitsFrontmatterFromBody() {
        String raw = """
                ---
                id: character_kael
                type: character
                ---
                # Heading

                Body text.
                """;
        FrontmatterParser.Split split = parser.split(raw);
        assertThat(split.yamlText()).contains("id: character_kael").contains("type: character");
        assertThat(split.body()).startsWith("# Heading").contains("Body text.");
    }

    @Test
    void noFrontmatterLeavesBodyUntouched() {
        String raw = "# Just a note\n\nNo frontmatter here.\n";
        FrontmatterParser.Split split = parser.split(raw);
        assertThat(split.yamlText()).isEmpty();
        assertThat(split.body()).isEqualTo(raw);
    }

    @Test
    void unclosedFrontmatterLeavesBodyUntouched() {
        String raw = "---\nid: x\ntype: y\nno closing delim\n";
        FrontmatterParser.Split split = parser.split(raw);
        assertThat(split.yamlText()).isEmpty();
        assertThat(split.body()).isEqualTo(raw);
    }

    @Test
    void parsesYamlIntoMap() {
        String yaml = """
                id: character_kael
                type: character
                aliases: [Kael, Scout]
                """;
        FrontmatterParser.Parsed parsed = parser.parse(yaml, file);
        assertThat(parsed.issues()).isEmpty();
        assertThat(parsed.fields()).containsEntry("id", "character_kael").containsEntry("type", "character");
        assertThat(parsed.fields().get("aliases")).isInstanceOf(java.util.List.class);
    }

    @Test
    void malformedYamlSurfacesAsParseError() {
        String yaml = "id: [unterminated";
        FrontmatterParser.Parsed parsed = parser.parse(yaml, file);
        assertThat(parsed.issues()).anySatisfy(issue ->
                assertThat(issue.category()).isEqualTo(ValidationCategory.PARSE_ERRORS));
    }

    @Test
    void scalarFrontmatterRejectedAsParseError() {
        FrontmatterParser.Parsed parsed = parser.parse("just a string", file);
        assertThat(parsed.issues()).anySatisfy(issue ->
                assertThat(issue.category()).isEqualTo(ValidationCategory.PARSE_ERRORS));
    }
}
