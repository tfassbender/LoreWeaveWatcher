package com.tfassbender.loreweave.watch.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigTest {

    @Test
    void defaultsAreSane() {
        Config c = Config.defaults();
        assertThat(c.theme()).isEqualTo("dark");
        assertThat(c.autoOpenBrowser()).isTrue();
        assertThat(c.pollIntervalMs()).isEqualTo(1000);
        assertThat(c.idleShutdown().enabled()).isTrue();
        assertThat(c.ignorePaths()).isEmpty();
        assertThat(c.validationError()).isNull();
    }

    @Test
    void roundTripsThroughJson() {
        Config original = new Config("light", false, 1500,
                new Config.IdleShutdownConfig(true, 12_000, 25_000),
                List.of("drafts/", "scratch.md"));
        String json = Json.render(original.toJson());
        Config restored = Config.fromJson(JsonReader.parseObject(json));
        assertThat(restored).isEqualTo(original);
    }

    @Test
    void thresholdMustExceedPollIntervalByFiveSeconds() {
        Config tooSmall = new Config("dark", true, 2000,
                new Config.IdleShutdownConfig(true, 6_000, 30_000), List.of());
        assertThat(tooSmall.validationError()).contains("threshold_ms");
        Config justRight = new Config("dark", true, 2000,
                new Config.IdleShutdownConfig(true, 7_000, 30_000), List.of());
        assertThat(justRight.validationError()).isNull();
    }

    @Test
    void disabledIdleSkipsValidation() {
        Config c = new Config("dark", true, 5000,
                new Config.IdleShutdownConfig(false, 100, 0), List.of());
        assertThat(c.validationError()).isNull();
    }

    @Test
    void compactConstructorClampsAndNormalizes() {
        Config c = new Config("LIGHT", true, 50, null, null);
        assertThat(c.theme()).isEqualTo("light");
        assertThat(c.pollIntervalMs()).isEqualTo(200);
        assertThat(c.idleShutdown()).isEqualTo(Config.IdleShutdownConfig.DEFAULTS);
        assertThat(c.ignorePaths()).isEmpty();
    }

    @Test
    void invalidThemeFallsBackToDark() {
        Config c = new Config("solarized", true, 1000,
                Config.IdleShutdownConfig.DEFAULTS, List.of());
        assertThat(c.theme()).isEqualTo("dark");
    }

    @Test
    void storeRoundTripsToDisk(@TempDir Path tmp) throws IOException {
        ConfigStore store = new ConfigStore(tmp.resolve("lore-weave-watch.json"));
        assertThat(store.load()).isEqualTo(Config.defaults());

        Config saved = new Config("light", false, 1500,
                new Config.IdleShutdownConfig(true, 12_000, 25_000),
                List.of("drafts/"));
        store.save(saved);
        assertThat(store.file()).exists();

        Config reloaded = store.load();
        assertThat(reloaded).isEqualTo(saved);
    }

    @Test
    void storeRejectsInconsistentSave(@TempDir Path tmp) {
        ConfigStore store = new ConfigStore(tmp.resolve("lore-weave-watch.json"));
        Config bad = new Config("dark", true, 2000,
                new Config.IdleShutdownConfig(true, 4_000, 0), List.of());
        assertThatThrownBy(() -> store.save(bad))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void storeIgnoresMalformedFileAndReturnsDefaults(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("lore-weave-watch.json");
        java.nio.file.Files.writeString(file, "{ this is not valid json");
        ConfigStore store = new ConfigStore(file);
        assertThat(store.load()).isEqualTo(Config.defaults());
    }
}
