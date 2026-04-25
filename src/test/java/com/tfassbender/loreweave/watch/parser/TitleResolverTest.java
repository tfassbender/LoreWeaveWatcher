package com.tfassbender.loreweave.watch.parser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TitleResolverTest {

    private final TitleResolver resolver = new TitleResolver();

    @Test
    void usesFrontmatterTitleWhenPresent() {
        TitleResolver.Resolved r = resolver.resolve("Kael Varyn", "kael");
        assertThat(r.title()).isEqualTo("Kael Varyn");
        assertThat(r.missingTitleWarning()).isFalse();
    }

    @Test
    void fallsBackToFilenameAndWarns() {
        TitleResolver.Resolved r = resolver.resolve(null, "kael");
        assertThat(r.title()).isEqualTo("kael");
        assertThat(r.missingTitleWarning()).isTrue();
    }

    @Test
    void blankFrontmatterTitleIsTreatedAsAbsent() {
        TitleResolver.Resolved r = resolver.resolve("  ", "fallback");
        assertThat(r.title()).isEqualTo("fallback");
        assertThat(r.missingTitleWarning()).isTrue();
    }

    @Test
    void emptyFallbackYieldsEmptyTitleAndWarning() {
        TitleResolver.Resolved r = resolver.resolve(null, null);
        assertThat(r.title()).isEmpty();
        assertThat(r.missingTitleWarning()).isTrue();
    }
}
