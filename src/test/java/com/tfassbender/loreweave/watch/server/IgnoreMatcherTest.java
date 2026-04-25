package com.tfassbender.loreweave.watch.server;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

class IgnoreMatcherTest {

    @Test
    void emptyOrNullMatchesNothing() {
        assertThat(IgnoreMatcher.from(null).test("anything.md")).isFalse();
        assertThat(IgnoreMatcher.from(List.of()).test("anything.md")).isFalse();
    }

    @Test
    void exactFileMatch() {
        Predicate<String> p = IgnoreMatcher.from(List.of("drafts/scratch.md"));
        assertThat(p.test("drafts/scratch.md")).isTrue();
        assertThat(p.test("drafts/scratch.md.bak")).isFalse();
        assertThat(p.test("scratch.md")).isFalse();
    }

    @Test
    void directoryPrefixWithTrailingSlash() {
        Predicate<String> p = IgnoreMatcher.from(List.of("drafts/"));
        assertThat(p.test("drafts")).isTrue();
        assertThat(p.test("drafts/note.md")).isTrue();
        assertThat(p.test("drafts/sub/deep.md")).isTrue();
        assertThat(p.test("drafts2/note.md")).isFalse();
    }

    @Test
    void directoryWithoutTrailingSlashStillMatches() {
        Predicate<String> p = IgnoreMatcher.from(List.of("drafts"));
        assertThat(p.test("drafts/note.md")).isTrue();
        assertThat(p.test("drafts")).isTrue();
        assertThat(p.test("draftsy/note.md")).isFalse();
    }

    @Test
    void backslashPatternsAreNormalized() {
        Predicate<String> p = IgnoreMatcher.from(List.of("drafts\\inner\\"));
        assertThat(p.test("drafts/inner/note.md")).isTrue();
    }
}
