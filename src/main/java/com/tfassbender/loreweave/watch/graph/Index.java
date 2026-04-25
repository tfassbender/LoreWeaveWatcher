package com.tfassbender.loreweave.watch.graph;

import com.tfassbender.loreweave.watch.domain.NoteKey;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * An immutable snapshot of a parsed vault. Notes are keyed by their normalized
 * vault-relative path (see {@link NoteKey}). A sync wipes and rebuilds the
 * index; readers always see a consistent snapshot.
 */
public record Index(Map<String, IndexedNote> notesByKey, ValidationReport report) {

    public Index {
        notesByKey = notesByKey == null ? Map.of() : Map.copyOf(notesByKey);
        if (report == null) report = new ValidationReport(Map.of());
    }

    /** Look up a note by its already-normalized key. */
    public Optional<IndexedNote> get(String key) {
        return Optional.ofNullable(notesByKey.get(key));
    }

    /** Look up a note by a raw path or link text — normalizes before querying. */
    public Optional<IndexedNote> getByPath(String path) {
        return get(NoteKey.of(path));
    }

    public Optional<IndexedNote> getByPath(Path path) {
        return get(NoteKey.of(path));
    }

    public Collection<IndexedNote> notes() {
        return notesByKey.values();
    }

    public int size() {
        return notesByKey.size();
    }
}
