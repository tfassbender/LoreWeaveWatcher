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
import java.util.function.Predicate;

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
        return build(vaultRoot, p -> false);
    }

    /**
     * Watcher extension: skips any vault-relative path (POSIX-normalized) for
     * which {@code excluded.test(path)} returns true. Threaded through to
     * {@link VaultScanner#scan(Path, Predicate)}. See COPYING_NOTES.md.
     */
    public Index build(Path vaultRoot, Predicate<String> excluded) {
        ValidationReport.Builder reportBuilder = new ValidationReport.Builder();
        // Watcher divergence: keep the raw issue list so /api/validation can
        // surface every issue with its full path + message (the report keeps
        // only per-category counts + 5 sample paths).
        List<ValidationIssue> allIssues = new ArrayList<>();

        VaultScanner.Result scan = scanner.scan(vaultRoot, excluded);
        reportBuilder.addAll(scan.issues());
        allIssues.addAll(scan.issues());

        // Parse every file. Track which ones succeed.
        // Any unexpected throwable from the assembler is caught and surfaced as a
        // PARSE_ERRORS issue so a single malformed file cannot crash a vault scan
        // — the dashboard then reports it instead of the server going dark.
        List<Parsed> served = new ArrayList<>();
        for (VaultScanner.ScannedFile file : scan.files()) {
            ParseResult result;
            try {
                result = assembler.assemble(file.relativePath(), file.content());
            } catch (RuntimeException ex) {
                ValidationIssue issue = ValidationIssue.error(
                        ValidationCategory.PARSE_ERRORS, file.relativePath(),
                        "internal parser error: " + ex.getClass().getSimpleName()
                                + (ex.getMessage() == null ? "" : ": " + ex.getMessage()));
                reportBuilder.add(issue);
                allIssues.add(issue);
                continue;
            }
            reportBuilder.addAll(result.issues());
            allIssues.addAll(result.issues());
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

        // Watcher divergence: scan each served note's tags for #todo / #todo/...
        // and emit one info-severity issue per occurrence so the dashboard can
        // surface them as a checklist. Tags are already lowercased and dedup'd
        // by HashtagExtractor, so we get one issue per unique todo per note.
        for (Parsed p : served) {
            for (String tag : p.note.tags()) {
                if (tag.equals("todo") || tag.startsWith("todo/")) {
                    ValidationIssue issue = ValidationIssue.info(
                            ValidationCategory.TODO_TAGS, p.file.relativePath(),
                            "#" + tag);
                    reportBuilder.add(issue);
                    allIssues.add(issue);
                }
            }
        }

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
                    ValidationIssue issue = ValidationIssue.error(
                            ValidationCategory.UNRESOLVED_LINKS, p.file.relativePath(),
                            "unresolved [[" + link.rawTarget() + "]] in '" + srcKey + "'");
                    reportBuilder.add(issue);
                    allIssues.add(issue);
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

        int notesExcluded = scan.files().size() - served.size();
        return new Index(notes, reportBuilder.build(), allIssues, notesExcluded);
    }

    private record Parsed(VaultScanner.ScannedFile file, Note note) {}
}
