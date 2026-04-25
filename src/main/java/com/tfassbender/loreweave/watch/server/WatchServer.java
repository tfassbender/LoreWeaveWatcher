package com.tfassbender.loreweave.watch.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.tfassbender.loreweave.watch.graph.Index;
import com.tfassbender.loreweave.watch.graph.IndexBuilder;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Tiny HTTP server that serves the watcher dashboard and a single
 * {@code /api/validation} endpoint. Binds to {@code 127.0.0.1} only (no remote
 * access). When the preferred port is bound, falls back to an OS-picked port.
 *
 * <p>Each {@code /api/validation} request rebuilds the index from disk —
 * vaults parse in single-digit milliseconds, so polling once per second is
 * cheap and avoids needing a {@code WatchService}.
 */
public final class WatchServer {

    public static final int DEFAULT_PORT = 5717;
    private static final String UI_RESOURCE = "/com/tfassbender/loreweave/watch/ui/index.html";

    private final HttpServer server;
    private final Path vault;
    private final IndexBuilder builder;
    private final IdleShutdown idle;

    private WatchServer(HttpServer server, Path vault, IndexBuilder builder, IdleShutdown idle) {
        this.server = server;
        this.vault = vault;
        this.builder = builder;
        this.idle = idle;
    }

    public static WatchServer start(Path vault, int preferredPort, Runnable onIdleShutdown) throws IOException {
        HttpServer server = bind(preferredPort);
        IdleShutdown idle = new IdleShutdown(() -> {
            try {
                onIdleShutdown.run();
            } catch (RuntimeException ignored) {
                // never let a callback crash the scheduler thread
            }
        });
        WatchServer ws = new WatchServer(server, vault, new IndexBuilder(), idle);
        server.createContext("/api/validation", ws::handleValidation);
        server.createContext("/", ws::handleRoot);
        server.setExecutor(null);
        server.start();
        idle.start();
        return ws;
    }

    private static HttpServer bind(int preferredPort) throws IOException {
        InetSocketAddress addr = new InetSocketAddress("127.0.0.1", preferredPort);
        try {
            return HttpServer.create(addr, 0);
        } catch (BindException e) {
            return HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        }
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public URI url() {
        return URI.create("http://127.0.0.1:" + port() + "/");
    }

    public void stop() {
        idle.stop();
        server.stop(0);
    }

    /**
     * Best-effort browser launch via {@link Desktop}. Prints the URL when the
     * environment is headless or doesn't support {@code BROWSE}.
     */
    public void openBrowser() {
        URI url = url();
        if (Desktop.isDesktopSupported()) {
            Desktop d = Desktop.getDesktop();
            if (d.isSupported(Desktop.Action.BROWSE)) {
                try {
                    d.browse(url);
                    return;
                } catch (IOException ignored) {
                    // fall through to print
                }
            }
        }
        System.out.println("open in browser: " + url);
    }

    private void handleValidation(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            send(ex, 405, "text/plain", "method not allowed".getBytes(StandardCharsets.UTF_8));
            return;
        }
        idle.recordPoll();
        Index index = builder.build(vault);
        String body = ValidationApi.render(index, Instant.now());
        send(ex, 200, "application/json; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    private void handleRoot(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            send(ex, 405, "text/plain", "method not allowed".getBytes(StandardCharsets.UTF_8));
            return;
        }
        if (!"/".equals(ex.getRequestURI().getPath())) {
            send(ex, 404, "text/plain", "not found".getBytes(StandardCharsets.UTF_8));
            return;
        }
        byte[] html;
        try (InputStream in = WatchServer.class.getResourceAsStream(UI_RESOURCE)) {
            if (in == null) {
                send(ex, 500, "text/plain", "ui resource missing".getBytes(StandardCharsets.UTF_8));
                return;
            }
            html = in.readAllBytes();
        }
        send(ex, 200, "text/html; charset=utf-8", html);
    }

    private static void send(HttpExchange ex, int status, String contentType, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }
}
