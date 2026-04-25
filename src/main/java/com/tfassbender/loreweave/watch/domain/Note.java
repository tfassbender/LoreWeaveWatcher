package com.tfassbender.loreweave.watch.domain;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * A successfully parsed note. The note's stable handle is its
 * {@link #sourcePath} — the vault-relative path — normalized via
 * {@link NoteKey#of(Path)} for lookups.
 *
 * <p>Forward links are unresolved at this stage (raw link text, no target
 * handle). Resolution happens during index build. Backlinks are not stored on
 * {@link Note}; they are computed and exposed by the {@code Index}.
 */
public record Note(
        String type,
        String title,
        String summary,
        int schemaVersion,
        List<String> aliases,
        Map<String, Object> metadata,
        String body,
        List<Link> links,
        List<String> tags,
        Path sourcePath) {

    public Note {
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        links = links == null ? List.of() : List.copyOf(links);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    /** The normalized lookup handle for this note (vault-relative path, lowercased, forward-slash, {@code .md}-stripped). */
    public String key() {
        return NoteKey.of(sourcePath);
    }
}
