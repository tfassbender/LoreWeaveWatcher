package com.tfassbender.loreweave.watch.parser;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Code;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.Text;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts inline {@code #hashtags} by walking {@code Text} nodes of the
 * CommonMark AST. Obsidian-style nested tags ({@code #project/subtask}) are
 * supported. Tags are lowercased and deduplicated, preserving first-seen order.
 *
 * <p>Walking the AST means {@code #} in code blocks, heading markers, URL
 * fragments, and wiki-link heading fragments is naturally ignored — those
 * characters never reach a {@link Text} node.
 */
public final class HashtagExtractor {

    private static final Pattern HASHTAG = Pattern.compile("(?<=^|\\s)#([A-Za-z0-9_/-]+)");

    public List<String> extract(Node root) {
        Set<String> out = new LinkedHashSet<>();
        if (root == null) {
            return new ArrayList<>(out);
        }
        root.accept(new AbstractVisitor() {
            @Override
            public void visit(Text text) {
                scanText(text.getLiteral(), out);
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

            @Override
            public void visit(Link link) {
                // Skip the URL/destination of markdown links, but still visit the link's
                // Text children (the label), which is what super.visit does.
                visitChildren(link);
            }
        });
        return new ArrayList<>(out);
    }

    private static void scanText(String literal, Set<String> out) {
        if (literal == null || literal.isEmpty()) {
            return;
        }
        Matcher m = HASHTAG.matcher(literal);
        while (m.find()) {
            // Reject tags that are only digits (e.g. "#123" is typically an issue/PR reference,
            // not a classifier). A tag must contain at least one letter, underscore, slash, or hyphen.
            String tag = m.group(1);
            if (tag.chars().allMatch(Character::isDigit)) {
                continue;
            }
            out.add(tag.toLowerCase(java.util.Locale.ROOT));
        }
    }
}
