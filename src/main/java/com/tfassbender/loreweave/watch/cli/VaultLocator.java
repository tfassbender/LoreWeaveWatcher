package com.tfassbender.loreweave.watch.cli;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Locale;

/**
 * Resolves the vault root from either an explicit override or from this jar's
 * own install location. Default rule: when running from
 * {@code <vault>/.loreweave/lore-weave-watch.jar}, the parent-of-parent of the
 * jar is the vault. If that directory has no markdown files, walk upward until
 * one is found — gives a sane fallback when running from an IDE (jar location
 * lands somewhere under {@code build/}) or when the user dropped the jar one
 * level too deep.
 */
public final class VaultLocator {

    private VaultLocator() {}

    public static Path locate(Path override) {
        if (override != null) {
            if (!Files.isDirectory(override)) {
                throw new VaultDetectionException("--vault path is not a directory: " + override);
            }
            return override.toAbsolutePath().normalize();
        }
        return locateFromJarLocation(ownCodeSourceLocation());
    }

    static Path locateFromJarLocation(Path codeSourceLocation) {
        Path installDir = Files.isDirectory(codeSourceLocation)
                ? codeSourceLocation
                : codeSourceLocation.getParent();
        if (installDir == null) {
            throw new VaultDetectionException(
                    "could not determine install directory from " + codeSourceLocation);
        }
        Path start = installDir.getParent();
        if (start == null) start = installDir;

        Path cursor = start;
        while (cursor != null) {
            if (containsMarkdown(cursor)) {
                return cursor.toAbsolutePath().normalize();
            }
            cursor = cursor.getParent();
        }
        throw new VaultDetectionException(
                "no .md files found at " + start + " or any ancestor; pass --vault <path>");
    }

    static boolean containsMarkdown(Path dir) {
        if (!Files.isDirectory(dir)) return false;
        boolean[] found = {false};
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) {
                    if (d.equals(dir)) return FileVisitResult.CONTINUE;
                    String name = d.getFileName() == null ? "" : d.getFileName().toString();
                    if (name.startsWith(".")) return FileVisitResult.SKIP_SUBTREE;
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                    String name = f.getFileName().toString();
                    if (name.toLowerCase(Locale.ROOT).endsWith(".md")) {
                        found[0] = true;
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path f, IOException e) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            return false;
        }
        return found[0];
    }

    private static Path ownCodeSourceLocation() {
        ProtectionDomain pd = VaultLocator.class.getProtectionDomain();
        CodeSource cs = pd == null ? null : pd.getCodeSource();
        URL url = cs == null ? null : cs.getLocation();
        if (url == null) {
            throw new VaultDetectionException("could not locate own code source; pass --vault <path>");
        }
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new VaultDetectionException("malformed code source URL: " + url);
        }
    }

    public static final class VaultDetectionException extends RuntimeException {
        public VaultDetectionException(String message) { super(message); }
    }
}
