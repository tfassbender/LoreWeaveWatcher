package com.tfassbender.loreweave.watch.cli;

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
            case WATCH -> {
                System.out.println("watch mode is not implemented yet (phase 4).");
                yield 0;
            }
            case CHECK -> {
                System.out.println("check mode is not implemented yet (phase 8).");
                yield 0;
            }
            case ERROR -> {
                System.err.println("error: " + parsed.errorMessage());
                System.err.println();
                System.err.println(helpText());
                yield 64;
            }
        };
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
