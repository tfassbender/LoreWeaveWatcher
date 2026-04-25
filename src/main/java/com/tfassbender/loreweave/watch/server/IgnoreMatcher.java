package com.tfassbender.loreweave.watch.server;

import java.util.List;
import java.util.function.Predicate;

/**
 * Builds a predicate over POSIX-normalized vault-relative paths from the
 * user's {@code ignore_paths} config. Match rules:
 *
 * <ul>
 *   <li>Exact match — {@code drafts/scratch.md} hides that single file.</li>
 *   <li>Directory prefix — {@code drafts/} or {@code drafts} hides anything
 *       inside that directory tree (matches the directory itself and all
 *       descendants).</li>
 * </ul>
 *
 * <p>Comparison is case-sensitive on case-sensitive filesystems; the input
 * strings are normalized so the user can type backslashes on Windows and the
 * matcher still works against the forward-slash relative paths.
 */
public final class IgnoreMatcher {

    private IgnoreMatcher() {}

    public static Predicate<String> from(List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) return p -> false;
        List<String> normalized = patterns.stream()
                .filter(p -> p != null && !p.isBlank())
                .map(IgnoreMatcher::normalize)
                .toList();
        if (normalized.isEmpty()) return p -> false;
        return path -> {
            if (path == null) return false;
            String norm = normalize(path);
            for (String pat : normalized) {
                if (matches(norm, pat)) return true;
            }
            return false;
        };
    }

    static boolean matches(String relativePath, String pattern) {
        if (pattern.isEmpty()) return false;
        String p = stripTrailingSlash(pattern);
        if (relativePath.equals(p)) return true;
        // Directory prefix: pattern matches if relativePath starts with "p/".
        return relativePath.startsWith(p + "/");
    }

    static String normalize(String s) {
        return s.replace('\\', '/').trim();
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
