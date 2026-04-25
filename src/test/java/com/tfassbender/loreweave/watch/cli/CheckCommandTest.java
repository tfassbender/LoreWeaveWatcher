package com.tfassbender.loreweave.watch.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CheckCommandTest {

    private static Path validVault() {
        return Path.of("src/test/resources/vault-valid").toAbsolutePath();
    }

    private static Path invalidVault() {
        return Path.of("src/test/resources/vault-invalid").toAbsolutePath();
    }

    private static Captured run(Args args, Path vault) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ByteArrayOutputStream e = new ByteArrayOutputStream();
        int code = CheckCommand.run(args, vault,
                new PrintStream(o, true, StandardCharsets.UTF_8),
                new PrintStream(e, true, StandardCharsets.UTF_8));
        return new Captured(code, o.toString(StandardCharsets.UTF_8), e.toString(StandardCharsets.UTF_8));
    }

    record Captured(int exit, String out, String err) {}

    @Test
    void cleanVaultExitsZeroAndReportsClean() {
        Captured r = run(args(false, Args.Severity.ALL), validVault());
        assertThat(r.exit).isEqualTo(0);
        assertThat(r.out).contains("notes served");
        assertThat(r.out).contains("no issues to report");
    }

    @Test
    void invalidVaultExitsTwoForErrors() {
        Captured r = run(args(false, Args.Severity.ALL), invalidVault());
        assertThat(r.exit).isEqualTo(2);
        assertThat(r.out).contains("[error]");
        assertThat(r.out).contains("[warning]");
    }

    @Test
    void severityErrorsHidesWarningsButKeepsExitCodeForErrors() {
        Captured r = run(args(false, Args.Severity.ERRORS), invalidVault());
        assertThat(r.exit).isEqualTo(2);
        // Should still print errors, but no warnings section
        assertThat(r.out).contains("[error]");
        assertThat(r.out).doesNotContain("[warning]");
    }

    @Test
    void severityWarningsKeepsExitCodeForErrorsButFiltersOutput() {
        Captured r = run(args(false, Args.Severity.WARNINGS), invalidVault());
        // Errors still drive the exit code (CI shouldn't lie about a broken vault).
        assertThat(r.exit).isEqualTo(2);
        // But the printed list only shows warnings.
        assertThat(r.out).doesNotContain("[error]");
        assertThat(r.out).contains("[warning]");
    }

    @Test
    void jsonOutputMatchesApiShape() {
        Captured r = run(args(true, Args.Severity.ALL), invalidVault());
        assertThat(r.exit).isEqualTo(2);
        assertThat(r.out.trim()).startsWith("{\"vault\":");
        assertThat(r.out).contains("\"summary\":");
        assertThat(r.out).contains("\"issues\":");
        assertThat(r.out).contains("\"scanned_at\":");
    }

    @Test
    void exitCodeForOnlyWarnings() {
        // Synthesize: vault-invalid mixes errors+warnings, so we use the unit
        // helper directly to cover the "warnings only" branch.
        java.util.List<com.tfassbender.loreweave.watch.domain.ValidationIssue> warnOnly = java.util.List.of(
                com.tfassbender.loreweave.watch.domain.ValidationIssue.warning(
                        com.tfassbender.loreweave.watch.domain.ValidationCategory.MISSING_TITLE,
                        Path.of("a.md"), "msg"));
        assertThat(CheckCommand.exitCodeFor(warnOnly, Args.Severity.ALL)).isEqualTo(1);
        assertThat(CheckCommand.exitCodeFor(warnOnly, Args.Severity.ERRORS)).isEqualTo(0);
        assertThat(CheckCommand.exitCodeFor(java.util.List.of(), Args.Severity.ALL)).isEqualTo(0);
    }

    private static Args args(boolean json, Args.Severity sev) {
        return new Args(Args.Action.CHECK, null, null, json, sev, null);
    }
}
