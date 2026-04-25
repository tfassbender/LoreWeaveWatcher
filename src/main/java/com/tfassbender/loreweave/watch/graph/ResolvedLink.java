package com.tfassbender.loreweave.watch.graph;

import com.tfassbender.loreweave.watch.domain.Link;

import java.util.Optional;

/**
 * A {@link Link} paired with the outcome of resolving it against the served
 * index. {@code targetKey} is present iff resolution succeeded; its value is
 * the normalized path handle of the target note.
 */
public record ResolvedLink(Link link, Optional<String> targetKey) {

    public ResolvedLink {
        if (link == null) throw new IllegalArgumentException("link is required");
        if (targetKey == null) targetKey = Optional.empty();
    }

    public boolean isResolved() {
        return targetKey.isPresent();
    }
}
