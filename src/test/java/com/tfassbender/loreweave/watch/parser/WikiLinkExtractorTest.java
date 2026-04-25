package com.tfassbender.loreweave.watch.parser;

import com.tfassbender.loreweave.watch.domain.Link;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class WikiLinkExtractorTest {

    private final MarkdownBodyParser md = new MarkdownBodyParser();
    private final WikiLinkExtractor extractor = new WikiLinkExtractor();

    private List<Link> extract(String body) {
        return extractor.extract(md.parse(body));
    }

    @Test
    void extractsPlainWikiLink() {
        assertThat(extract("See [[Kael Varyn]] for details."))
                .extracting(Link::rawTarget, Link::displayText, Link::fragment)
                .containsExactly(tuple("Kael Varyn", null, null));
    }

    @Test
    void stripsPipeDisplay() {
        assertThat(extract("[[Faction - Outer Union|Union]]"))
                .extracting(Link::rawTarget, Link::displayText, Link::fragment)
                .containsExactly(tuple("Faction - Outer Union", "Union", null));
    }

    @Test
    void stripsHeadingFragment() {
        assertThat(extract("[[Kael Varyn#Backstory]]"))
                .extracting(Link::rawTarget, Link::fragment)
                .containsExactly(tuple("Kael Varyn", "Backstory"));
    }

    @Test
    void stripsBlockFragment() {
        assertThat(extract("[[Kael#^abc123]]"))
                .extracting(Link::rawTarget, Link::fragment)
                .containsExactly(tuple("Kael", "^abc123"));
    }

    @Test
    void embedsAreIgnored() {
        assertThat(extract("Inline ![[diagram.png]] and ![[page]] are embeds.")).isEmpty();
    }

    @Test
    void attachmentLinksAreDropped() {
        // Non-note targets (images, pdfs, audio, video, archives) are ignored —
        // they're valid Obsidian links but LoreWeave doesn't index attachments,
        // so we drop them at extraction time rather than flag unresolved_links.
        assertThat(extract("See [[document.pdf]] and [[diagram.png]] and [[theme.mp3]].")).isEmpty();
    }

    @Test
    void attachmentsMixedWithNoteLinksOnlyDropAttachments() {
        assertThat(extract("A [[note]] and [[cover.jpg]] together."))
                .extracting(Link::rawTarget)
                .containsExactly("note");
    }

    @Test
    void unknownExtensionIsKeptAsNoteLink() {
        // A target like [[v1.5]] has a "extension" of "5" which isn't in the
        // attachment set — treat it as a note link, not an attachment.
        assertThat(extract("Version [[v1.5]] notes."))
                .extracting(Link::rawTarget)
                .containsExactly("v1.5");
    }

    @Test
    void ignoresLinksInsideInlineCode() {
        assertThat(extract("`[[not a link]]` — right?")).isEmpty();
    }

    @Test
    void ignoresLinksInsideFencedCodeBlock() {
        String body = """
                ```
                [[Not a link either]]
                ```
                """;
        assertThat(extract(body)).isEmpty();
    }

    @Test
    void extractsMultipleLinksFromOneLine() {
        assertThat(extract("A [[One]] then [[Two|Second]] then [[Three#h]]."))
                .extracting(Link::rawTarget)
                .containsExactly("One", "Two", "Three");
    }
}
