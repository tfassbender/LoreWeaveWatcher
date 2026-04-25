package com.tfassbender.loreweave.watch.domain;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Normalization helpers for the string handle by which a note is looked up.
 *
 * <p>The handle is a vault-relative path, normalized so that comparisons work
 * uniformly across Windows/Linux and across how a link was written:
 * backslashes become forward slashes, a trailing {@code .md} suffix is
 * stripped, and the whole string is lowercased. This is the form stored in
 * {@code Index} keys, {@code ResolvedLink.targetKey}, and
 * {@code Backlink.sourceKey}.
 */
public final class NoteKey {

    private NoteKey() {}

    public static String of(Path vaultRelativePath) {
        if (vaultRelativePath == null) return "";
        return of(vaultRelativePath.toString());
    }

    public static String of(String vaultRelativePath) {
        if (vaultRelativePath == null) return "";
        String s = vaultRelativePath.replace('\\', '/').trim();
        if (s.toLowerCase(Locale.ROOT).endsWith(".md")) {
            s = s.substring(0, s.length() - 3);
        }
        // Collapse leading "./" if present.
        if (s.startsWith("./")) s = s.substring(2);
        return s.toLowerCase(Locale.ROOT);
    }
}
