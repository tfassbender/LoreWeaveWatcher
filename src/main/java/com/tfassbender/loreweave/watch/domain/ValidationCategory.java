package com.tfassbender.loreweave.watch.domain;

/**
 * Categories surfaced by the {@code /health} endpoint. The first three are
 * errors (the note is excluded from the served index); the next three are
 * warnings (the note is served but incomplete); {@link #TODO_TAGS} is a
 * watcher-only informational category (the note is fine — it just contains
 * a {@code #todo} hashtag the author wants to track).
 *
 * <p>Note-identity validation categories like {@code invalid_id_format} and
 * {@code duplicate_ids} don't exist because LoreWeave uses the vault-relative
 * path as a note's handle — the filesystem guarantees uniqueness and Obsidian
 * keeps {@code [[wiki-links]]} consistent across moves.
 *
 * <p><b>Watcher divergence:</b> {@link #TODO_TAGS} and the {@link Severity#INFO}
 * tier do not exist in the upstream LoreWeave repo. Kept watcher-local so the
 * dashboard can surface {@code #todo} tags without re-using error/warning
 * styling.
 */
public enum ValidationCategory {
    // Errors
    PARSE_ERRORS(Severity.ERROR),
    MISSING_REQUIRED_FIELDS(Severity.ERROR),
    UNRESOLVED_LINKS(Severity.ERROR),

    // Warnings
    MISSING_TITLE(Severity.WARNING),
    MISSING_SUMMARY(Severity.WARNING),
    MISSING_SCHEMA_VERSION(Severity.WARNING),

    // Infos (watcher-only)
    TODO_TAGS(Severity.INFO);

    private final Severity severity;

    ValidationCategory(Severity severity) {
        this.severity = severity;
    }

    public Severity severity() {
        return severity;
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    public boolean isWarning() {
        return severity == Severity.WARNING;
    }

    public boolean isInfo() {
        return severity == Severity.INFO;
    }

    public enum Severity { ERROR, WARNING, INFO }
}
