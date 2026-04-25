package com.tfassbender.loreweave.watch.parser;

import com.tfassbender.loreweave.watch.domain.Link;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Code;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.node.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts {@code [[wiki-style]]} links from a CommonMark AST. Embeds
 * ({@code ![[...]]}) are ignored. Fragments ({@code #heading}, {@code #^block})
 * and pipe-display ({@code |display}) are split out so the rawTarget is ready
 * for resolution.
 *
 * <p>Only {@code Text} nodes are scanned — so a {@code [[x]]} inside inline code
 * or a fenced code block is naturally ignored.
 */
public final class WikiLinkExtractor {

    // [[ followed by anything that isn't ]] or a newline. We capture the whole
    // inside and split on | / # afterwards, so we don't need complex groups.
    private static final Pattern WIKI_LINK = Pattern.compile("\\[\\[([^\\]\\n]+)]]");

    /**
     * File extensions recognized as non-note attachments. Links whose raw target
     * ends in one of these are silently dropped — the vault may contain
     * {@code [[diagram.png]]} or {@code [[deck.pdf]]}, but LoreWeave does not
     * index attachments, so such links are ignored rather than flagged as
     * unresolved.
     */
    private static final Set<String> ATTACHMENT_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "svg", "webp", "bmp", "tiff", "heic",
            "pdf",
            "mp3", "wav", "ogg", "m4a", "flac",
            "mp4", "mov", "webm", "mkv", "avi",
            "zip", "tar", "gz", "7z",
            "canvas");

    public List<Link> extract(Node root) {
        List<Link> out = new ArrayList<>();
        if (root == null) {
            return out;
        }
        root.accept(new AbstractVisitor() {
            @Override
            public void visit(Text text) {
                scanText(text, out);
            }

            @Override
            public void visit(Code code) {
                // Skip inline code.
            }

            @Override
            public void visit(FencedCodeBlock block) {
                // Skip fenced code blocks.
            }

            @Override
            public void visit(IndentedCodeBlock block) {
                // Skip indented code blocks.
            }
        });
        return out;
    }

    private static void scanText(Text text, List<Link> out) {
        String literal = text.getLiteral();
        if (literal == null || literal.isEmpty()) {
            return;
        }
        Matcher m = WIKI_LINK.matcher(literal);
        while (m.find()) {
            int start = m.start();
            // Embeds: a '!' immediately before [[ turns this into a transclusion.
            if (start > 0 && literal.charAt(start - 1) == '!') {
                continue;
            }
            String inner = m.group(1).trim();
            if (inner.isEmpty()) {
                continue;
            }
            Link link = parseInner(inner);
            if (isAttachmentTarget(link.rawTarget())) {
                continue;
            }
            out.add(link);
        }
    }

    private static boolean isAttachmentTarget(String rawTarget) {
        int dot = rawTarget.lastIndexOf('.');
        if (dot < 0 || dot == rawTarget.length() - 1) return false;
        String ext = rawTarget.substring(dot + 1).toLowerCase(Locale.ROOT);
        return ATTACHMENT_EXTENSIONS.contains(ext);
    }

    private static Link parseInner(String inner) {
        String displayText = null;
        int pipe = inner.indexOf('|');
        if (pipe >= 0) {
            displayText = inner.substring(pipe + 1).trim();
            inner = inner.substring(0, pipe).trim();
            if (displayText.isEmpty()) {
                displayText = null;
            }
        }
        String fragment = null;
        int hash = inner.indexOf('#');
        if (hash >= 0) {
            fragment = inner.substring(hash + 1).trim();
            inner = inner.substring(0, hash).trim();
            if (fragment.isEmpty()) {
                fragment = null;
            }
        }
        return new Link(inner, displayText, fragment);
    }
}
