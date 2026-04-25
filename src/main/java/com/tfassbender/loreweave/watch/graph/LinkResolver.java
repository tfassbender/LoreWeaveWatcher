package com.tfassbender.loreweave.watch.graph;

import com.tfassbender.loreweave.watch.domain.Link;
import com.tfassbender.loreweave.watch.domain.NoteKey;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Resolves a {@link Link}'s raw target to a normalized note key using the
 * order defined in {@code doc/vault_schema.md}: full path → basename (both
 * comparisons case-insensitive, {@code .md} suffix ignored).
 *
 * <p>Aliases declared in frontmatter are kept as note metadata but are
 * <b>not</b> consulted during link resolution. If a link text is not a path
 * or a basename, it is unresolved.
 *
 * <p>When two notes share a basename, the first-inserted entry wins. Callers
 * feed the resolver a deterministic note order (sorted by source path), so
 * the tie-break is reproducible across runs.
 */
public final class LinkResolver {

    private final Map<String, String> byFullPath; // normalized key → target key
    private final Map<String, String> byBasename; // lowercased basename → target key

    private LinkResolver(Map<String, String> byFullPath, Map<String, String> byBasename) {
        this.byFullPath = byFullPath;
        this.byBasename = byBasename;
    }

    /** Returns the normalized target key (as produced by {@link NoteKey}), or empty. */
    public Optional<String> resolve(Link link) {
        if (link == null) return Optional.empty();
        String raw = link.rawTarget();
        if (raw == null || raw.isBlank()) return Optional.empty();

        String fullKey = NoteKey.of(raw);
        String id = byFullPath.get(fullKey);
        if (id != null) return Optional.of(id);

        String basenameKey = basenameOf(raw);
        id = byBasename.get(basenameKey);
        if (id != null) return Optional.of(id);

        return Optional.empty();
    }

    /** Builder that captures the index tables while notes are added in order. */
    public static final class Builder {
        // Preserve insertion order so "first wins" tie-breaking is deterministic.
        private final Map<String, String> byFullPath = new LinkedHashMap<>();
        private final Map<String, String> byBasename = new LinkedHashMap<>();

        public Builder add(Path relativePath) {
            if (relativePath == null) return this;

            String key = NoteKey.of(relativePath);
            byFullPath.putIfAbsent(key, key);

            Path name = relativePath.getFileName();
            if (name != null) {
                String basenameKey = basenameOf(name.toString());
                if (!basenameKey.isEmpty()) byBasename.putIfAbsent(basenameKey, key);
            }

            return this;
        }

        public LinkResolver build() {
            return new LinkResolver(Map.copyOf(byFullPath), Map.copyOf(byBasename));
        }
    }

    private static String basenameOf(String raw) {
        String s = raw.replace('\\', '/').trim();
        int slash = s.lastIndexOf('/');
        if (slash >= 0) s = s.substring(slash + 1);
        if (s.toLowerCase(Locale.ROOT).endsWith(".md")) {
            s = s.substring(0, s.length() - 3);
        }
        return s.toLowerCase(Locale.ROOT);
    }

    // For debugging/tests.
    Map<String, String> byFullPath() { return new TreeMap<>(byFullPath); }
    Map<String, String> byBasename() { return new TreeMap<>(byBasename); }
}
