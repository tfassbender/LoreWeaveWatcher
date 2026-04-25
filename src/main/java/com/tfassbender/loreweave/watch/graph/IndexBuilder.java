package com.tfassbender.loreweave.watch.graph;

import com.tfassbender.loreweave.watch.domain.Backlink;
import com.tfassbender.loreweave.watch.domain.Link;
import com.tfassbender.loreweave.watch.domain.Note;
import com.tfassbender.loreweave.watch.domain.ValidationCategory;
import com.tfassbender.loreweave.watch.domain.ValidationIssue;
import com.tfassbender.loreweave.watch.parser.NoteAssembler;
import com.tfassbender.loreweave.watch.parser.ParseResult;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds an immutable {@link Index} from a vault on disk. The pipeline:
 *
 * <ol>
 *   <li>Scan the vault root for {@code .md} files.</li>
 *   <li>Parse each file with {@link NoteAssembler}. Warnings ride along;
 *       error-severity issues mark the note as excluded.</li>
 *   <li>Build a {@link LinkResolver} over the served notes (so links to an
 *       excluded note surface as {@code unresolved_links}).</li>
 *   <li>Resolve each served note's forward links; unresolved targets emit
 *       {@code unresolved_links} issues against the source note.</li>
 *   <li>Compute backlinks by inverting the resolved forward edges.</li>
 * </ol>
 *
 * <p>Notes are keyed by the normalized vault-relative path. There is no
 * duplicate-identity check — the filesystem already guarantees unique paths.
 */
public final class IndexBuilder {

    private final VaultScanner scanner;
    private final NoteAssembler assembler;

    public IndexBuilder() {
        this(new VaultScanner(), new NoteAssembler());
    }

    public IndexBuilder(VaultScanner scanner, NoteAssembler assembler) {
        this.scanner = scanner;
        this.assembler = assembler;
    }

    public Index build(Path vaultRoot) {
        ValidationReport.Builder reportBuilder = new ValidationReport.Builder();

        VaultScanner.Result scan = scanner.scan(vaultRoot);
        reportBuilder.addAll(scan.issues());

        // Parse every file. Track which ones succeed.
        List<Parsed> served = new ArrayList<>();
        for (VaultScanner.ScannedFile file : scan.files()) {
            ParseResult result = assembler.assemble(file.relativePath(), file.content());
            reportBuilder.addAll(result.issues());
            if (result instanceof ParseResult.Success s) {
                served.add(new Parsed(file, s.note()));
            }
        }

        // Build the resolver over the served notes only.
        LinkResolver.Builder resolverBuilder = new LinkResolver.Builder();
        for (Parsed p : served) {
            resolverBuilder.add(p.file.relativePath());
        }
        LinkResolver resolver = resolverBuilder.build();

        // Resolve forward links for each served note.
        Map<String, List<ResolvedLink>> resolvedByKey = new HashMap<>();
        Map<String, List<Backlink>> backlinksByTarget = new HashMap<>();
        for (Parsed p : served) {
            Note n = p.note;
            String srcKey = n.key();
            List<ResolvedLink> links = new ArrayList<>(n.links().size());
            for (Link link : n.links()) {
                Optional<String> target = resolver.resolve(link);
                links.add(new ResolvedLink(link, target));
                if (target.isPresent()) {
                    backlinksByTarget
                            .computeIfAbsent(target.get(), k -> new ArrayList<>())
                            .add(new Backlink(srcKey, link.displayText()));
                } else {
                    reportBuilder.add(ValidationIssue.error(
                            ValidationCategory.UNRESOLVED_LINKS, p.file.relativePath(),
                            "unresolved [[" + link.rawTarget() + "]] in '" + srcKey + "'"));
                }
            }
            resolvedByKey.put(srcKey, links);
        }

        // Assemble the served index map.
        Map<String, IndexedNote> notes = new LinkedHashMap<>();
        for (Parsed p : served) {
            String key = p.note.key();
            List<ResolvedLink> links = resolvedByKey.getOrDefault(key, List.of());
            List<Backlink> backs = backlinksByTarget.getOrDefault(key, List.of());
            notes.put(key, new IndexedNote(p.note, links, backs));
        }

        return new Index(notes, reportBuilder.build());
    }

    private record Parsed(VaultScanner.ScannedFile file, Note note) {}
}
