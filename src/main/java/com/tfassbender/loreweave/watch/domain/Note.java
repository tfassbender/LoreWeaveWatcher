package com.tfassbender.loreweave.watch.domain;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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
        // Use null-tolerant defensive copies. YAML frontmatter routinely produces
        // null values (e.g. an empty `metadata: { faction: , role: }` from an
        // unfilled template), and List.copyOf / Map.copyOf reject nulls — so the
        // hardened JDK collections would crash a scan on otherwise-fine notes.
        aliases = aliases == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(aliases));
        metadata = metadata == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        links = links == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(links));
        tags = tags == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(tags));
    }

    /** The normalized lookup handle for this note (vault-relative path, lowercased, forward-slash, {@code .md}-stripped). */
    public String key() {
        return NoteKey.of(sourcePath);
    }
}
