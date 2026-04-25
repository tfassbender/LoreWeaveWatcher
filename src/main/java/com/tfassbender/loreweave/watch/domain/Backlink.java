package com.tfassbender.loreweave.watch.domain;

/**
 * An incoming link to a note, computed on index build from the forward links
 * of every other note.
 *
 * @param sourceKey    the normalized path key of the note that links to us
 *                     (see {@link NoteKey})
 * @param displayText  the pipe-display text used at the link site, or
 *                     {@code null} if absent
 */
public record Backlink(String sourceKey, String displayText) {

    public Backlink {
        if (sourceKey == null || sourceKey.isBlank()) {
            throw new IllegalArgumentException("sourceKey must be non-blank");
        }
    }
}
