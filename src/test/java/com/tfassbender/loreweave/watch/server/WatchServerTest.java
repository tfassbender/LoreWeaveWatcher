package com.tfassbender.loreweave.watch.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

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

    @AfterEach
    void tearDown() {
        if (server != null) server.stop();
    }

    @Test
    void servesValidationJsonAgainstFixture() throws IOException {
        server = WatchServer.start(
                Path.of("src/test/resources/vault-valid").toAbsolutePath(),
                0, () -> {});
        assertThat(server.port()).isPositive();

        String body = httpGet(server.url().resolve("/api/validation"));
        assertThat(body).startsWith("{\"summary\":");
        assertThat(body).contains("\"notes_served\":");
        assertThat(body).contains("\"issues\":[");
        assertThat(body).contains("\"scanned_at\":\"");
    }

    @Test
    void servesIndexHtmlAtRoot() throws IOException {
        server = WatchServer.start(
                Path.of("src/test/resources/vault-valid").toAbsolutePath(),
                0, () -> {});

        String body = httpGet(server.url());
        assertThat(body).contains("<!DOCTYPE html>");
        assertThat(body).contains("lore-weave-watch");
    }

    @Test
    void unknownPathReturns404() throws IOException {
        server = WatchServer.start(
                Path.of("src/test/resources/vault-valid").toAbsolutePath(),
                0, () -> {});

        HttpURLConnection conn = (HttpURLConnection) server.url().resolve("/nope").toURL().openConnection();
        assertThat(conn.getResponseCode()).isEqualTo(404);
        conn.disconnect();
    }

    @Test
    void boundPortFallsBackToOsPicked() throws IOException {
        server = WatchServer.start(
                Path.of("src/test/resources/vault-valid").toAbsolutePath(),
                0, () -> {});
        int firstPort = server.port();

        // Try to start a second server on the same explicit port -> should fall back.
        WatchServer second = WatchServer.start(
                Path.of("src/test/resources/vault-valid").toAbsolutePath(),
                firstPort, () -> {});
        try {
            assertThat(second.port()).isNotEqualTo(firstPort).isPositive();
        } finally {
            second.stop();
        }
    }

    @Test
    void pollResetsIdleAcrossRequests() throws IOException, InterruptedException {
        AtomicBoolean fired = new AtomicBoolean();
        server = WatchServer.start(
                Path.of("src/test/resources/vault-valid").toAbsolutePath(),
                0, () -> fired.set(true));

        // Hit /api/validation a couple of times. With default 30s grace + 10s
        // threshold this should not fire during the test.
        httpGet(server.url().resolve("/api/validation"));
        Thread.sleep(50);
        httpGet(server.url().resolve("/api/validation"));

        assertThat(fired).isFalse();
    }

    private static String httpGet(URI url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.toURL().openConnection();
        conn.setRequestMethod("GET");
        try (InputStream in = conn.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            conn.disconnect();
        }
    }
}
