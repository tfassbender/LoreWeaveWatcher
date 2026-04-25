package com.tfassbender.loreweave.watch.graph;

import com.tfassbender.loreweave.watch.domain.ValidationCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class VaultScannerTest {

    private final VaultScanner scanner = new VaultScanner();

    @Test
    void walksMarkdownFilesAndSkipsDottedDirs(@TempDir Path vault) throws IOException {
        Files.createDirectories(vault.resolve("characters"));
        Files.createDirectories(vault.resolve(".git/objects"));
        Files.createDirectories(vault.resolve(".obsidian/plugins"));
        Files.createDirectories(vault.resolve(".trash"));
        Files.createDirectories(vault.resolve(".custom-dot"));

        Files.writeString(vault.resolve("characters/kael.md"), "kael", StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("characters/rex.md"), "rex", StandardCharsets.UTF_8);
        Files.writeString(vault.resolve(".git/objects/a.md"), "skip", StandardCharsets.UTF_8);
        Files.writeString(vault.resolve(".obsidian/plugins/b.md"), "skip", StandardCharsets.UTF_8);
        Files.writeString(vault.resolve(".trash/c.md"), "skip", StandardCharsets.UTF_8);
        Files.writeString(vault.resolve(".custom-dot/d.md"), "skip", StandardCharsets.UTF_8);
        Files.writeString(vault.resolve("characters/image.png"), "not md", StandardCharsets.UTF_8);

        VaultScanner.Result result = scanner.scan(vault);

        assertThat(result.issues()).isEmpty();
        assertThat(result.files())
                .extracting(f -> f.relativePath().toString().replace('\\', '/'))
                .containsExactly("characters/kael.md", "characters/rex.md");
    }

    @Test
    void returnsParseErrorWhenVaultRootIsNotADirectory(@TempDir Path tmp) throws IOException {
        Path notADir = tmp.resolve("file.txt");
        Files.writeString(notADir, "hi");

        VaultScanner.Result result = scanner.scan(notADir);

        assertThat(result.files()).isEmpty();
        assertThat(result.issues())
                .extracting(i -> i.category())
                .containsExactly(ValidationCategory.PARSE_ERRORS);
    }

    @Test
    void fixtureVaultValidIsReadable() {
        Path root = Path.of("src/test/resources/vault-valid").toAbsolutePath();
        VaultScanner.Result result = scanner.scan(root);

        assertThat(result.issues()).isEmpty();
        assertThat(result.files())
                .extracting(f -> f.relativePath().toString().replace('\\', '/'))
                .containsExactlyInAnyOrder(
                        "characters/kael.md",
                        "characters/rex.md",
                        "characters/zara.md",
                        "characters/tarek.md",
                        "locations/karsis.md",
                        "factions/union.md",
                        "factions/inner-union.md",
                        "events/border-incident.md",
                        "events/karsis-siege.md",
                        "items/void-crystal.md");
    }
}
