package com.tfassbender.loreweave.watch.domain;

/**
 * Categories surfaced by the {@code /health} endpoint. The first three are
 * errors (the note is excluded from the served index); the last three are
 * warnings (the note is served but incomplete).
 *
 * <p>Note-identity validation categories like {@code invalid_id_format} and
 * {@code duplicate_ids} don't exist because LoreWeave uses the vault-relative
 * path as a note's handle — the filesystem guarantees uniqueness and Obsidian
 * keeps {@code [[wiki-links]]} consistent across moves.
 */
public enum ValidationCategory {
    // Errors
    PARSE_ERRORS,
    MISSING_REQUIRED_FIELDS,
    UNRESOLVED_LINKS,

    // Warnings
    MISSING_TITLE,
    MISSING_SUMMARY,
    MISSING_SCHEMA_VERSION;

    public boolean isError() {
        return ordinal() <= UNRESOLVED_LINKS.ordinal();
    }
}
