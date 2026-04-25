package com.tfassbender.loreweave.watch.cli;

import com.tfassbender.loreweave.watch.domain.ValidationCategory;
import com.tfassbender.loreweave.watch.domain.ValidationIssue;
import com.tfassbender.loreweave.watch.graph.Index;
import com.tfassbender.loreweave.watch.graph.IndexBuilder;
import com.tfassbender.loreweave.watch.server.ConfigStore;
import com.tfassbender.loreweave.watch.server.IgnoreMatcher;
import com.tfassbender.loreweave.watch.server.ValidationApi;

import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * One-shot headless validation. Runs the same {@link IndexBuilder} that the
 * watch server uses, formats the result for stdout, and returns an exit code:
 *
 * <ul>
 *   <li>{@code 0} — no errors, no warnings (or warnings filtered out by {@code --severity})</li>
 *   <li>{@code 1} — warnings only</li>
 *   <li>{@code 2} — at least one error</li>
 *   <li>{@code 3} — could not scan (vault detection failure, etc.) — surfaced via {@link Main}</li>
 * </ul>
 *
 * <p>The watcher's {@code ignore_paths} config is honoured here too, so the
 * scripted validator and the dashboard agree on what is in scope. Other config
 * fields (theme, idle timers, …) are irrelevant in check mode.
 */
public final class CheckCommand {

    private CheckCommand() {}

    public static int run(Args args, Path vault, PrintStream out, PrintStream err) {
        Index index;
        try {
            ConfigStore store = new ConfigStore(vault.resolve(".loreweave").resolve("lore-weave-watch.json"));
            var ignore = IgnoreMatcher.from(store.load().ignorePaths());
            index = new IndexBuilder().build(vault, ignore);
        } catch (RuntimeException e) {
            err.println("error: scan failed: " + e.getMessage());
            return 3;
        }

        Args.Severity sev = args.severity() == null ? Args.Severity.ALL : args.severity();
        List<ValidationIssue> filtered = filter(index.issues(), sev);

        if (args.json()) {
            out.println(ValidationApi.render(index, Instant.now(), vault));
        } else {
            printHuman(out, vault, index, filtered);
        }

        return exitCodeFor(index.issues(), sev);
    }

    static int exitCodeFor(List<ValidationIssue> all, Args.Severity sev) {
        // Exit code reflects the actual vault state, not just the filter:
        // - errors present => 2 regardless of --severity (ignoring errors via
        //   --severity=warnings would otherwise hide real failures from CI)
        // - --severity=errors AND only warnings exist => 0 (user asked to ignore them)
        // - warnings only => 1
        // - clean => 0
        boolean hasError = all.stream().anyMatch(ValidationIssue::isError);
        boolean hasWarning = all.stream().anyMatch(i -> !i.isError());
        if (hasError) return 2;
        if (hasWarning && sev != Args.Severity.ERRORS) return 1;
        return 0;
    }

    static List<ValidationIssue> filter(List<ValidationIssue> issues, Args.Severity sev) {
        return switch (sev) {
            case ALL -> issues;
            case ERRORS -> issues.stream().filter(ValidationIssue::isError).toList();
            case WARNINGS -> issues.stream().filter(i -> !i.isError()).toList();
        };
    }

    private static void printHuman(PrintStream out, Path vault, Index index, List<ValidationIssue> filtered) {
        int errors = index.report().totalErrors();
        int warnings = index.report().totalWarnings();
        int served = index.size();
        int excluded = index.notesExcluded();

        out.println("vault: " + vault);
        out.println(served + " notes served"
                + (excluded > 0 ? " / " + excluded + " excluded" : "")
                + ", " + errors + " error" + (errors == 1 ? "" : "s")
                + ", " + warnings + " warning" + (warnings == 1 ? "" : "s"));

        if (filtered.isEmpty()) {
            out.println("no issues to report.");
            return;
        }

        // Group by (severity desc, category asc) to match the dashboard's order.
        Comparator<ValidationIssue> order = Comparator
                .<ValidationIssue, Integer>comparing(i -> i.isError() ? 0 : 1)
                .thenComparing(i -> i.category().name())
                .thenComparing(i -> normalize(i.filePath()))
                .thenComparing(ValidationIssue::message);
        List<ValidationIssue> sorted = new ArrayList<>(filtered);
        sorted.sort(order);

        ValidationCategory currentCat = null;
        boolean currentError = false;
        for (ValidationIssue issue : sorted) {
            if (issue.category() != currentCat || issue.isError() != currentError) {
                out.println();
                out.println("[" + (issue.isError() ? "error" : "warning") + "] "
                        + issue.category().name().toLowerCase(Locale.ROOT));
                currentCat = issue.category();
                currentError = issue.isError();
            }
            String firstLine = singleLine(issue.message());
            out.println("  " + normalize(issue.filePath()) + " : " + firstLine);
        }
    }

    private static String normalize(Path p) {
        if (p == null) return "";
        return p.toString().replace('\\', '/');
    }

    private static String singleLine(String message) {
        if (message == null) return "";
        int nl = message.indexOf('\n');
        return nl < 0 ? message : message.substring(0, nl) + " [...]";
    }
}
