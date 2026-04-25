package com.tfassbender.loreweave.watch.graph;

import com.tfassbender.loreweave.watch.domain.Backlink;
import com.tfassbender.loreweave.watch.domain.Note;

import java.util.List;

/**
 * The index's serving unit: a parsed {@link Note} plus its resolved forward
 * links and its computed backlinks.
 */
public record IndexedNote(Note note, List<ResolvedLink> resolvedLinks, List<Backlink> backlinks) {

    public IndexedNote {
        if (note == null) throw new IllegalArgumentException("note is required");
        resolvedLinks = resolvedLinks == null ? List.of() : List.copyOf(resolvedLinks);
        backlinks = backlinks == null ? List.of() : List.copyOf(backlinks);
    }
}
