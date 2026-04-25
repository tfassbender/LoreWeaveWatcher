package com.tfassbender.loreweave.watch.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads and saves the JSON config file at a fixed location. Missing file = use
 * defaults; malformed file = log and use defaults (so a typo doesn't brick the
 * dashboard). Save is best-effort atomic via a temp file rename.
 */
public final class ConfigStore {

    private final Path file;

    public ConfigStore(Path file) {
        this.file = file;
    }

    public Path file() { return file; }

    public Config load() {
        if (!Files.isRegularFile(file)) return Config.defaults();
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            return Config.fromJson(JsonReader.parseObject(text));
        } catch (IOException | RuntimeException e) {
            System.err.println("warning: could not read " + file + ": " + e.getMessage()
                    + " — using defaults");
            return Config.defaults();
        }
    }

    public void save(Config config) throws IOException {
        String err = config.validationError();
        if (err != null) throw new IllegalArgumentException(err);
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, Json.renderPretty(config.toJson()), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
