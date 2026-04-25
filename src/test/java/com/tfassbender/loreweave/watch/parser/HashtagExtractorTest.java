package com.tfassbender.loreweave.watch.parser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HashtagExtractorTest {

    private final MarkdownBodyParser md = new MarkdownBodyParser();
    private final HashtagExtractor extractor = new HashtagExtractor();

    private java.util.List<String> extract(String body) {
        return extractor.extract(md.parse(body));
    }

    @Test
    void extractsInlineHashtag() {
        assertThat(extract("Kael is a #pov character.")).containsExactly("pov");
    }

    @Test
    void lowercasesAndDeduplicates() {
        assertThat(extract("#POV and #pov and #Pov")).containsExactly("pov");
    }

    @Test
    void supportsNestedTags() {
        assertThat(extract("Scope: #project/arc/karsis")).containsExactly("project/arc/karsis");
    }

    @Test
    void ignoresHeadingMarker() {
        String body = """
                # This Heading

                Body text without tags.
                """;
        assertThat(extract(body)).isEmpty();
    }

    @Test
    void ignoresHashInUrlFragment() {
        assertThat(extract("See [docs](https://example.com/page#section).")).isEmpty();
    }

    @Test
    void ignoresHashInCodeBlock() {
        String body = """
                ```
                # not a tag
                #also/not
                ```
                """;
        assertThat(extract(body)).isEmpty();
    }

    @Test
    void ignoresWikiLinkHeadingFragment() {
        // The '#Chapter' is inside the [[…]] syntax, which CommonMark sees as plain text;
        // but our extractor's whitespace-boundary requirement means '[[Target#Chapter]]'
        // won't register '#Chapter' because '#' is preceded by a letter, not whitespace/SOL.
        assertThat(extract("[[Target#Chapter]]")).isEmpty();
    }

    @Test
    void rejectsPurelyNumericHashes() {
        assertThat(extract("Fixes #123 and tracked as #pov.")).containsExactly("pov");
    }

    @Test
    void preservesFirstSeenOrder() {
        assertThat(extract("#beta #alpha #beta #gamma")).containsExactly("beta", "alpha", "gamma");
    }
}
