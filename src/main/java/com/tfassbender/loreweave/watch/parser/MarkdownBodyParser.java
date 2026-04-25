package com.tfassbender.loreweave.watch.parser;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;

/** Thin wrapper around commonmark-java that exposes the parsed AST. */
public final class MarkdownBodyParser {

    private final Parser parser;

    public MarkdownBodyParser() {
        this.parser = Parser.builder().build();
    }

    public Node parse(String body) {
        return parser.parse(body == null ? "" : body);
    }
}
