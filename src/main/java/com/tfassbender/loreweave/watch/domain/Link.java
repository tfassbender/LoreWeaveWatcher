package com.tfassbender.loreweave.watch.domain;

/**
 * An unresolved {@code [[wiki-link]]} extracted from a note body. Resolution to
 * a target note ID happens later, during index build, because it requires the
 * global set of notes (path, alias, and basename tables).
 *
 * @param rawTarget   the link target as written, with {@code #fragment} and {@code |display} stripped
 * @param displayText the pipe-display text, or {@code null} if absent
 * @param fragment    the {@code #heading} or {@code #^block} fragment, or {@code null} if absent
 */
public record Link(String rawTarget, String displayText, String fragment) {

    public Link {
        if (rawTarget == null || rawTarget.isBlank()) {
            throw new IllegalArgumentException("rawTarget must be non-blank");
        }
    }
}
