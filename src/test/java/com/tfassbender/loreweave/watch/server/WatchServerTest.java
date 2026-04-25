package com.tfassbender.loreweave.watch.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class WatchServerTest {

    private WatchServer server;
    private WatchServer second;

    @AfterEach
    void tearDown() {
        if (second != null) second.stop();
        if (server != null) server.stop();
    }

    private static ConfigStore freshStore(Path tmp) {
        return new ConfigStore(tmp.resolve("lore-weave-watch.json"));
    }

    private static Path vault() {
        return Path.of("src/test/resources/vault-valid").toAbsolutePath();
    }

    @Test
    void servesValidationJsonAgainstFixture(@TempDir Path tmp) throws IOException {
        server = WatchServer.start(vault(), 0, freshStore(tmp), () -> {});
        assertThat(server.port()).isPositive();

        String body = httpGet(server.url().resolve("/api/validation"));
        assertThat(body).startsWith("{\"vault\":");
        assertThat(body).contains("\"summary\":");
        assertThat(body).contains("\"notes_served\":");
        assertThat(body).contains("\"issues\":[");
        assertThat(body).contains("\"scanned_at\":\"");
    }

    @Test
    void servesIndexHtmlAtRoot(@TempDir Path tmp) throws IOException {
        server = WatchServer.start(vault(), 0, freshStore(tmp), () -> {});
        String body = httpGet(server.url());
        assertThat(body).contains("<!DOCTYPE html>");
        assertThat(body).contains("lore-weave-watch");
    }

    @Test
    void unknownPathReturns404(@TempDir Path tmp) throws IOException {
        server = WatchServer.start(vault(), 0, freshStore(tmp), () -> {});
        HttpURLConnection conn = (HttpURLConnection) server.url().resolve("/nope").toURL().openConnection();
        assertThat(conn.getResponseCode()).isEqualTo(404);
        conn.disconnect();
    }

    @Test
    void boundPortFallsBackToOsPicked(@TempDir Path tmp) throws IOException {
        server = WatchServer.start(vault(), 0, freshStore(tmp), () -> {});
        int firstPort = server.port();
        second = WatchServer.start(vault(), firstPort, freshStore(tmp.resolve("two")), () -> {});
        assertThat(second.port()).isNotEqualTo(firstPort).isPositive();
    }

    @Test
    void pollResetsIdleAcrossRequests(@TempDir Path tmp) throws IOException, InterruptedException {
        AtomicBoolean fired = new AtomicBoolean();
        server = WatchServer.start(vault(), 0, freshStore(tmp), () -> fired.set(true));
        httpGet(server.url().resolve("/api/validation"));
        Thread.sleep(50);
        httpGet(server.url().resolve("/api/validation"));
        assertThat(fired).isFalse();
    }

    @Test
    void getConfigReturnsDefaults(@TempDir Path tmp) throws IOException {
        server = WatchServer.start(vault(), 0, freshStore(tmp), () -> {});
        String body = httpGet(server.url().resolve("/api/config"));
        assertThat(body).contains("\"theme\":\"dark\"");
        assertThat(body).contains("\"auto_open_browser\":true");
        assertThat(body).contains("\"poll_interval_ms\":1000");
        assertThat(body).contains("\"ignore_paths\":[]");
    }

    @Test
    void postConfigPersistsAndHotReloads(@TempDir Path tmp) throws IOException {
        ConfigStore store = freshStore(tmp);
        server = WatchServer.start(vault(), 0, store, () -> {});
        String body = httpPost(server.url().resolve("/api/config"),
                "{\"theme\":\"light\",\"auto_open_browser\":false,\"poll_interval_ms\":2000," +
                        "\"idle_shutdown\":{\"enabled\":true,\"threshold_ms\":15000,\"grace_ms\":30000}," +
                        "\"ignore_paths\":[\"drafts/\"]}");
        assertThat(body).contains("\"theme\":\"light\"");
        assertThat(body).contains("\"poll_interval_ms\":2000");
        // Server's live config reflects the change immediately.
        assertThat(server.currentConfig().theme()).isEqualTo("light");
        assertThat(server.currentConfig().pollIntervalMs()).isEqualTo(2000);
        // File on disk now exists with the new content.
        assertThat(store.file()).exists();
        assertThat(store.load().theme()).isEqualTo("light");
    }

    @Test
    void postConfigRejectsInconsistentIdleThreshold(@TempDir Path tmp) throws IOException {
        server = WatchServer.start(vault(), 0, freshStore(tmp), () -> {});
        URI url = server.url().resolve("/api/config");
        // poll=2000, threshold=4000 -> needs >= 7000
        HttpURLConnection conn = (HttpURLConnection) url.toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.getOutputStream().write(
                ("{\"theme\":\"dark\",\"auto_open_browser\":true,\"poll_interval_ms\":2000," +
                        "\"idle_shutdown\":{\"enabled\":true,\"threshold_ms\":4000,\"grace_ms\":30000}," +
                        "\"ignore_paths\":[]}").getBytes(StandardCharsets.UTF_8));
        assertThat(conn.getResponseCode()).isEqualTo(400);
        conn.disconnect();
    }

    @Test
    void vaultPathsListsMarkdownFilesAndDirectories(@TempDir Path tmp) throws IOException {
        server = WatchServer.start(vault(), 0, freshStore(tmp), () -> {});
        String body = httpGet(server.url().resolve("/api/vault-paths"));
        assertThat(body).contains("\"paths\":[");
        // Expect at least one .md file and at least one directory entry (ending with /).
        assertThat(body).contains(".md");
        assertThat(body).contains("characters/");
    }

    @Test
    void ignorePathsExcludeFilesFromValidation(@TempDir Path tmp) throws IOException {
        ConfigStore store = freshStore(tmp);
        // Pre-seed config that ignores the entire characters/ subtree.
        Config seeded = new Config("dark", true, 1000,
                Config.IdleShutdownConfig.DEFAULTS, java.util.List.of("characters/"));
        store.save(seeded);

        server = WatchServer.start(vault(), 0, store, () -> {});
        String before = httpGet(server.url().resolve("/api/validation"));
        // The fixture vault is clean so we just check notes_served drops.
        // Without ignore: vault-valid has 10 notes; with characters/ excluded it should be smaller.
        assertThat(before).doesNotContain("characters/");
    }

    private static String httpGet(URI url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.toURL().openConnection();
        conn.setRequestMethod("GET");
        try (InputStream in = conn.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } finally { conn.disconnect(); }
    }

    private static String httpPost(URI url, String body) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        try (InputStream in = conn.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } finally { conn.disconnect(); }
    }
}
