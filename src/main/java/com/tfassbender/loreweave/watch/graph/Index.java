package com.tfassbender.loreweave.watch.graph;

import com.tfassbender.loreweave.watch.domain.NoteKey;
import com.tfassbender.loreweave.watch.domain.ValidationIssue;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An immutable snapshot of a parsed vault. Notes are keyed by their normalized
 * vault-relative path (see {@link NoteKey}). A sync wipes and rebuilds the
 * index; readers always see a consistent snapshot.
 *
 * <p><b>Watcher divergence from LoreWeave:</b> {@code issues} (full per-issue
 * list, not just the per-category samples kept by {@link ValidationReport}) and
 * {@code notesExcluded} are appended for the watcher's {@code /api/validation}
 * endpoint. {@link ValidationReport} keeps at most 5 samples per category; the
 * dashboard needs every issue with its full path + message. See
 * {@code COPYING_NOTES.md}.
 */
public record Index(
        Map<String, IndexedNote> notesByKey,
        ValidationReport report,
        List<ValidationIssue> issues,
        int notesExcluded) {

    public Index {
        notesByKey = notesByKey == null ? Map.of() : Map.copyOf(notesByKey);
        if (report == null) report = new ValidationReport(Map.of());
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public Index(Map<String, IndexedNote> notesByKey, ValidationReport report) {
        this(notesByKey, report, List.of(), 0);
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
