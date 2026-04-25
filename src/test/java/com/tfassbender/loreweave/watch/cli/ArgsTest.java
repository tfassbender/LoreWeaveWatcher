package com.tfassbender.loreweave.watch.cli;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ArgsTest {

    @Test
    void defaultsToWatch() {
        Args a = Args.parse(new String[]{});
        assertThat(a.action()).isEqualTo(Args.Action.WATCH);
        assertThat(a.vault()).isNull();
    }

    @Test
    void helpFlag() {
        assertThat(Args.parse(new String[]{"--help"}).action()).isEqualTo(Args.Action.HELP);
        assertThat(Args.parse(new String[]{"-h"}).action()).isEqualTo(Args.Action.HELP);
    }

    @Test
    void versionFlag() {
        assertThat(Args.parse(new String[]{"--version"}).action()).isEqualTo(Args.Action.VERSION);
        assertThat(Args.parse(new String[]{"-v"}).action()).isEqualTo(Args.Action.VERSION);
    }

    @Test
    void watchVaultOverride() {
        Args a = Args.parse(new String[]{"--vault", "/tmp/v"});
        assertThat(a.action()).isEqualTo(Args.Action.WATCH);
        assertThat(a.vault()).isEqualTo(Path.of("/tmp/v"));
    }

    @Test
    void watchPort() {
        Args a = Args.parse(new String[]{"--port", "9000"});
        assertThat(a.port()).isEqualTo(9000);
    }

    @Test
    void checkPositionalVault() {
        Args a = Args.parse(new String[]{"check", "/tmp/v"});
        assertThat(a.action()).isEqualTo(Args.Action.CHECK);
        assertThat(a.vault()).isEqualTo(Path.of("/tmp/v"));
    }

    @Test
    void checkJsonAndSeverity() {
        Args a = Args.parse(new String[]{"check", "--json", "--severity", "errors"});
        assertThat(a.json()).isTrue();
        assertThat(a.severity()).isEqualTo(Args.Severity.ERRORS);
    }

    @Test
    void unknownOptionIsError() {
        Args a = Args.parse(new String[]{"--bogus"});
        assertThat(a.action()).isEqualTo(Args.Action.ERROR);
        assertThat(a.errorMessage()).contains("--bogus");
    }

    @Test
    void invalidPortIsError() {
        assertThat(Args.parse(new String[]{"--port", "nope"}).action()).isEqualTo(Args.Action.ERROR);
    }

    @Test
    void invalidSeverityIsError() {
        assertThat(Args.parse(new String[]{"check", "--severity", "loud"}).action()).isEqualTo(Args.Action.ERROR);
    }

    @Test
    void missingValueIsError() {
        assertThat(Args.parse(new String[]{"--vault"}).action()).isEqualTo(Args.Action.ERROR);
    }
}
