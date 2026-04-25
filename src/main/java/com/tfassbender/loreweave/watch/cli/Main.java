package com.tfassbender.loreweave.watch.cli;

import java.nio.file.Path;

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
            case WATCH -> runWithVault(parsed, "watch mode is not implemented yet (phase 4).");
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
                  --port <n>                HTTP port (default 4718, watch mode only)
                  --json                    JSON output (check mode only)
                  --severity <level>        errors | warnings | all (check mode only, default all)
                  -h, --help                show this help and exit
                  -v, --version             show version and exit
                """;
    }
}
