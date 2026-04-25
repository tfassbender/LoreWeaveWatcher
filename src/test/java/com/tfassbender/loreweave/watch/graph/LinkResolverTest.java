package com.tfassbender.loreweave.watch.graph;

import com.tfassbender.loreweave.watch.domain.Link;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LinkResolverTest {

    private LinkResolver buildResolver() {
        return new LinkResolver.Builder()
                .add(Path.of("characters", "kael.md"))
                .add(Path.of("factions", "union.md"))
                .add(Path.of("locations", "karsis.md"))
                .build();
    }

    @Test
    void resolvesByBasenameCaseInsensitively() {
        LinkResolver r = buildResolver();
        assertThat(r.resolve(new Link("kael", null, null))).contains("characters/kael");
        assertThat(r.resolve(new Link("KAEL", null, null))).contains("characters/kael");
    }

    @Test
    void resolvesByFullPath() {
        LinkResolver r = buildResolver();
        assertThat(r.resolve(new Link("locations/karsis", null, null))).contains("locations/karsis");
        assertThat(r.resolve(new Link("locations/karsis.md", null, null))).contains("locations/karsis");
    }

    @Test
    void fullPathTakesPrecedenceOverBasename() {
        LinkResolver r = new LinkResolver.Builder()
                .add(Path.of("kael.md"))
                .add(Path.of("characters", "kael.md"))
                .build();
        // Both have basename 'kael'; first-wins says the root file takes the basename slot.
        assertThat(r.resolve(new Link("characters/kael", null, null))).contains("characters/kael");
        assertThat(r.resolve(new Link("kael", null, null))).contains("kael");
    }

    @Test
    void aliasLikeTextDoesNotResolve() {
        // Aliases are not consulted. Only path/basename.
        LinkResolver r = buildResolver();
        assertThat(r.resolve(new Link("The Scout", null, null))).isEmpty();
        assertThat(r.resolve(new Link("The Union", null, null))).isEmpty();
        assertThat(r.resolve(new Link("Rex Morrow", null, null))).isEmpty();
    }

    @Test
    void returnsEmptyWhenUnknown() {
        LinkResolver r = buildResolver();
        assertThat(r.resolve(new Link("Ghost", null, null))).isEmpty();
    }

    @Test
    void firstInsertedWinsOnBasenameCollision() {
        LinkResolver r = new LinkResolver.Builder()
                .add(Path.of("dir1", "note.md"))
                .add(Path.of("dir2", "note.md"))
                .build();
        assertThat(r.resolve(new Link("note", null, null))).contains("dir1/note");
    }

    @Test
    void backslashPathsNormalizedToForwardSlash() {
        LinkResolver r = new LinkResolver.Builder()
                .add(Path.of("characters", "kael.md"))
                .build();
        // A link written with Windows separators still resolves.
        assertThat(r.resolve(new Link("characters\\kael", null, null))).contains("characters/kael");
    }
}
