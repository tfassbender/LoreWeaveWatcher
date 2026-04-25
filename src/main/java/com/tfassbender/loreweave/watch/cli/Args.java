package com.tfassbender.loreweave.watch.cli;

import java.nio.file.Path;

public record Args(
        Action action,
        Path vault,
        Integer port,
        boolean json,
        Severity severity,
        String errorMessage) {

    public enum Action { WATCH, CHECK, HELP, VERSION, ERROR }

    public enum Severity { ERRORS, WARNINGS, ALL }

    public static Args parse(String[] argv) {
        Action action = Action.WATCH;
        Path vault = null;
        Integer port = null;
        boolean json = false;
        Severity severity = Severity.ALL;

        int i = 0;
        if (argv.length > 0 && "check".equals(argv[0])) {
            action = Action.CHECK;
            i = 1;
        }

        while (i < argv.length) {
            String a = argv[i];
            switch (a) {
                case "-h", "--help" -> {
                    return helpResult();
                }
                case "-v", "--version" -> {
                    return versionResult();
                }
                case "--vault" -> {
                    String v = next(argv, i, a);
                    if (v == null) return error("missing value for " + a);
                    vault = Path.of(v);
                    i += 2;
                    continue;
                }
                case "--port" -> {
                    String v = next(argv, i, a);
                    if (v == null) return error("missing value for " + a);
                    try {
                        port = Integer.parseInt(v);
                    } catch (NumberFormatException e) {
                        return error("invalid port: " + v);
                    }
                    i += 2;
                    continue;
                }
                case "--json" -> json = true;
                case "--severity" -> {
                    String v = next(argv, i, a);
                    if (v == null) return error("missing value for " + a);
                    severity = switch (v) {
                        case "errors" -> Severity.ERRORS;
                        case "warnings" -> Severity.WARNINGS;
                        case "all" -> Severity.ALL;
                        default -> null;
                    };
                    if (severity == null) return error("invalid --severity: " + v);
                    i += 2;
                    continue;
                }
                default -> {
                    if (a.startsWith("-")) return error("unknown option: " + a);
                    if (action == Action.CHECK && vault == null) {
                        vault = Path.of(a);
                    } else {
                        return error("unexpected argument: " + a);
                    }
                }
            }
            i++;
        }

        return new Args(action, vault, port, json, severity, null);
    }

    private static String next(String[] argv, int i, String flag) {
        if (i + 1 >= argv.length) return null;
        return argv[i + 1];
    }

    private static Args helpResult() {
        return new Args(Action.HELP, null, null, false, Severity.ALL, null);
    }

    private static Args versionResult() {
        return new Args(Action.VERSION, null, null, false, Severity.ALL, null);
    }

    private static Args error(String msg) {
        return new Args(Action.ERROR, null, null, false, Severity.ALL, msg);
    }
}
