package com.tfassbender.loreweave.watch.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.tfassbender.loreweave.watch.graph.Index;
import com.tfassbender.loreweave.watch.graph.IndexBuilder;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * Tiny HTTP server that serves the watcher dashboard and a small REST surface.
 * Binds to {@code 127.0.0.1} only (no remote access). When the preferred port
 * is bound, falls back to an OS-picked port.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /} — embedded {@code index.html}</li>
 *   <li>{@code GET /api/validation} — runs the index build, returns JSON</li>
 *   <li>{@code GET /api/config}, {@code POST /api/config} — read/write the on-disk config</li>
 *   <li>{@code GET /api/vault-paths} — every {@code .md} file plus every directory
 *       containing one, used by the settings dialog's ignore-path picker</li>
 * </ul>
 */
public final class WatchServer {

    public static final int DEFAULT_PORT = 5717;
    private static final String UI_RESOURCE = "/com/tfassbender/loreweave/watch/ui/index.html";

    private final HttpServer server;
    private final Path vault;
    private final IndexBuilder builder;
    private final IdleShutdown idle;
    private final ConfigStore configStore;
    private final AtomicReference<Config> liveConfig;

    private WatchServer(HttpServer server, Path vault, IndexBuilder builder, IdleShutdown idle,
                        ConfigStore configStore, AtomicReference<Config> liveConfig) {
        this.server = server;
        this.vault = vault;
        this.builder = builder;
        this.idle = idle;
        this.configStore = configStore;
        this.liveConfig = liveConfig;
    }

    public static WatchServer start(Path vault, int preferredPort, ConfigStore configStore,
                                    Runnable onIdleShutdown) throws IOException {
        HttpServer server = bind(preferredPort);
        Config initial = configStore.load();
        AtomicReference<Config> liveConfig = new AtomicReference<>(initial);

        IdleShutdown idle = new IdleShutdown(
                initial.idleShutdown().thresholdMs(),
                initial.idleShutdown().graceMs(),
                System::currentTimeMillis,
                () -> {
                    try { onIdleShutdown.run(); }
                    catch (RuntimeException ignored) { /* keep scheduler thread alive */ }
                });
        idle.update(initial.idleShutdown().enabled(),
                    initial.idleShutdown().thresholdMs(),
                    initial.idleShutdown().graceMs());

        WatchServer ws = new WatchServer(server, vault, new IndexBuilder(), idle, configStore, liveConfig);
        server.createContext("/api/validation", ws::handleValidation);
        server.createContext("/api/config", ws::handleConfig);
        server.createContext("/api/vault-paths", ws::handleVaultPaths);
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

    public int port() { return server.getAddress().getPort(); }
    public URI url()  { return URI.create("http://127.0.0.1:" + port() + "/"); }
    public Config currentConfig() { return liveConfig.get(); }

    public void stop() {
        idle.stop();
        server.stop(0);
    }

    /** Best-effort browser launch; honors {@code auto_open_browser} from config. */
    public void openBrowser() {
        if (!liveConfig.get().autoOpenBrowser()) {
            System.out.println("auto-open browser disabled by config; UI at " + url());
            return;
        }
        URI url = url();
        if (Desktop.isDesktopSupported()) {
            Desktop d = Desktop.getDesktop();
            if (d.isSupported(Desktop.Action.BROWSE)) {
                try { d.browse(url); return; }
                catch (IOException ignored) { /* fall through */ }
            }
        }
        System.out.println("open in browser: " + url);
    }

    // --- handlers -----------------------------------------------------------

    private void handleValidation(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { sendMethodNotAllowed(ex); return; }
        idle.recordPoll();
        Predicate<String> exclude = IgnoreMatcher.from(liveConfig.get().ignorePaths());
        Index index = builder.build(vault, exclude);
        String body = ValidationApi.render(index, Instant.now(), vault);
        sendJson(ex, 200, body);
    }

    private void handleConfig(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        if ("GET".equals(method)) {
            sendJson(ex, 200, Json.render(liveConfig.get().toJson()));
            return;
        }
        if ("POST".equals(method)) {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Config incoming;
            try {
                incoming = Config.fromJson(JsonReader.parseObject(body));
            } catch (RuntimeException e) {
                sendJson(ex, 400, errorJson("invalid JSON: " + e.getMessage()));
                return;
            }
            String err = incoming.validationError();
            if (err != null) { sendJson(ex, 400, errorJson(err)); return; }
            try { configStore.save(incoming); }
            catch (IOException io) { sendJson(ex, 500, errorJson("write failed: " + io.getMessage())); return; }
            liveConfig.set(incoming);
            idle.update(incoming.idleShutdown().enabled(),
                        incoming.idleShutdown().thresholdMs(),
                        incoming.idleShutdown().graceMs());
            sendJson(ex, 200, Json.render(incoming.toJson()));
            return;
        }
        sendMethodNotAllowed(ex);
    }

    private void handleVaultPaths(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { sendMethodNotAllowed(ex); return; }
        List<String> paths = collectVaultPaths();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("paths", paths);
        sendJson(ex, 200, Json.render(root));
    }

    /** All .md files plus every directory containing at least one .md, vault-relative POSIX. */
    private List<String> collectVaultPaths() {
        if (!Files.isDirectory(vault)) return List.of();
        TreeSet<String> sorted = new TreeSet<>();
        try {
            Files.walkFileTree(vault, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes a) {
                    if (dir.equals(vault)) return FileVisitResult.CONTINUE;
                    String name = dir.getFileName().toString();
                    if (name.startsWith(".")) return FileVisitResult.SKIP_SUBTREE;
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes a) {
                    String name = file.getFileName().toString();
                    if (!name.toLowerCase(Locale.ROOT).endsWith(".md")) return FileVisitResult.CONTINUE;
                    String rel = vault.relativize(file).toString().replace('\\', '/');
                    sorted.add(rel);
                    Path parent = file.getParent();
                    if (parent != null && !parent.equals(vault)) {
                        sorted.add(vault.relativize(parent).toString().replace('\\', '/') + "/");
                    }
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult visitFileFailed(Path f, IOException e) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) { /* return whatever we collected */ }
        return new ArrayList<>(sorted);
    }

    private void handleRoot(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { sendMethodNotAllowed(ex); return; }
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

    // --- helpers ------------------------------------------------------------

    private static void sendJson(HttpExchange ex, int status, String body) throws IOException {
        send(ex, status, "application/json; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    private static void sendMethodNotAllowed(HttpExchange ex) throws IOException {
        send(ex, 405, "text/plain", "method not allowed".getBytes(StandardCharsets.UTF_8));
    }

    private static String errorJson(String message) {
        return Json.render(Map.of("error", message));
    }

    private static void send(HttpExchange ex, int status, String contentType, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(status, body.length);
        try (var os = ex.getResponseBody()) { os.write(body); }
    }
}
