package com.tfassbender.loreweave.watch.graph;

import com.tfassbender.loreweave.watch.domain.ValidationCategory;
import com.tfassbender.loreweave.watch.domain.ValidationIssue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Recursively walks a vault directory and reads all {@code .md} files as UTF-8.
 * Directories beginning with {@code .} are skipped — this covers {@code .git/},
 * {@code .obsidian/}, {@code .trash/}, and any other hidden folders. IO failures
 * surface as {@link ValidationCategory#PARSE_ERRORS} issues rather than thrown
 * exceptions, so a single unreadable file does not abort a vault rebuild.
 *
 * <p>Output is deterministic (lexicographic by relative path) so downstream
 * tie-breaking (e.g. link resolution when multiple notes share a basename) is
 * reproducible.
 */
public final class VaultScanner {

    private static final Set<String> ALWAYS_SKIP = Set.of(".git", ".obsidian", ".trash");

    public Result scan(Path vaultRoot) {
        return scan(vaultRoot, p -> false);
    }

    /**
     * Watcher extension: takes a predicate that, given a vault-relative path
     * (POSIX-normalized, forward-slash), returns true to skip that file or
     * directory tree. Drives the {@code ignore_paths} config field. See
     * COPYING_NOTES.md for the divergence note.
     */
    public Result scan(Path vaultRoot, Predicate<String> excluded) {
        List<ScannedFile> files = new ArrayList<>();
        List<ValidationIssue> issues = new ArrayList<>();
        Predicate<String> exclude = excluded == null ? p -> false : excluded;

        if (vaultRoot == null || !Files.isDirectory(vaultRoot)) {
            issues.add(ValidationIssue.error(
                    ValidationCategory.PARSE_ERRORS,
                    vaultRoot == null ? Path.of("") : vaultRoot,
                    "vault root is not a directory: " + vaultRoot));
            return new Result(List.of(), issues);
        }

        try {
            Files.walkFileTree(vaultRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (dir.equals(vaultRoot)) return FileVisitResult.CONTINUE;
                    String name = dir.getFileName().toString();
                    String rel = vaultRoot.relativize(dir).toString().replace('\\', '/');
                    if (exclude.test(rel)) return FileVisitResult.SKIP_SUBTREE;
                    if (name.startsWith(".") || ALWAYS_SKIP.contains(name)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String name = file.getFileName().toString();
                    if (!name.endsWith(".md")) return FileVisitResult.CONTINUE;
                    String rel = vaultRoot.relativize(file).toString().replace('\\', '/');
                    if (exclude.test(rel)) return FileVisitResult.CONTINUE;
                    try {
                        String content = Files.readString(file, StandardCharsets.UTF_8);
                        files.add(new ScannedFile(file, vaultRoot.relativize(file), content));
                    } catch (IOException ex) {
                        issues.add(ValidationIssue.error(
                                ValidationCategory.PARSE_ERRORS, file,
                                "failed to read file: " + ex.getMessage()));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException ex) {
                    issues.add(ValidationIssue.error(
                            ValidationCategory.PARSE_ERRORS, file,
                            "failed to visit: " + ex.getMessage()));
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            issues.add(ValidationIssue.error(
                    ValidationCategory.PARSE_ERRORS, vaultRoot,
                    "vault walk failed: " + ex.getMessage()));
        }

        files.sort(Comparator.comparing(f -> f.relativePath().toString().replace('\\', '/')));
        return new Result(List.copyOf(files), List.copyOf(issues));
    }

    public record ScannedFile(Path absolutePath, Path relativePath, String content) {}

    public record Result(List<ScannedFile> files, List<ValidationIssue> issues) {}
}
