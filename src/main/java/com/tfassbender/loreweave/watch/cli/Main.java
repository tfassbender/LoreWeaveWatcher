package com.tfassbender.loreweave.watch.cli;

import com.tfassbender.loreweave.watch.server.WatchServer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

public final class Main {

    static final String VERSION = "0.1.0-SNAPSHOT";

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        Args parsed = Args.parse(args);
        return switch (parsed.action()) {
            case HELP -> {
                System.out.println(helpText());
                yield 0;
            }
            case VERSION -> {
                System.out.println("lore-weave-watch " + VERSION);
                yield 0;
            }
            case WATCH -> runWatch(parsed);
            case CHECK -> runWithVault(parsed, "check mode is not implemented yet (phase 8).");
            case ERROR -> {
                System.err.println("error: " + parsed.errorMessage());
                System.err.println();
                System.err.println(helpText());
                yield 64;
            }
        };
    }

    private static int runWithVault(Args parsed, String stubMessage) {
        try {
            Path vault = VaultLocator.locate(parsed.vault());
            System.out.println("vault: " + vault);
            System.out.println(stubMessage);
            return 0;
        } catch (VaultLocator.VaultDetectionException e) {
            System.err.println("error: " + e.getMessage());
            return 3;
        }
    }

    private static int runWatch(Args parsed) {
        Path vault;
        try {
            vault = VaultLocator.locate(parsed.vault());
        } catch (VaultLocator.VaultDetectionException e) {
            System.err.println("error: " + e.getMessage());
            return 3;
        }
        System.out.println("vault: " + vault);

        int port = parsed.port() == null ? WatchServer.DEFAULT_PORT : parsed.port();
        CountDownLatch done = new CountDownLatch(1);
        WatchServer server;
        try {
            server = WatchServer.start(vault, port, () -> {
                System.out.println("idle: no polls for ~10s, shutting down");
                done.countDown();
            });
        } catch (IOException e) {
            System.err.println("error: failed to start HTTP server: " + e.getMessage());
            return 3;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop();
            done.countDown();
        }, "lwwatch-shutdown"));

        System.out.println("listening on " + server.url());
        server.openBrowser();

        try {
            done.await();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        server.stop();
        return 0;
    }

    static String helpText() {
        return """
                lore-weave-watch - live validation dashboard for LoreWeave Obsidian vaults

                Usage:
                  java -jar lore-weave-watch.jar [options]
                  java -jar lore-weave-watch.jar check [options] [<vault>]

                Modes:
                  (default)   start the HTTP server + browser UI
                  check       run validation once, print report, exit

                Options:
                  --vault <path>            override vault auto-detection
                  --port <n>                HTTP port (default 5717, watch mode only)
                  --json                    JSON output (check mode only)
                  --severity <level>        errors | warnings | all (check mode only, default all)
                  -h, --help                show this help and exit
                  -v, --version             show version and exit
                """;
    }
}
