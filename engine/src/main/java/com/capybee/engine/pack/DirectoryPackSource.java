package com.capybee.engine.pack;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;

public final class DirectoryPackSource implements PackSource {
    private final Path root;

    public DirectoryPackSource(Path root) {
        try {
            this.root = root.toRealPath();
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot resolve pack directory", exception);
        }
    }

    public Optional<String> read(String relativePath) {
        if (!PackLayout.isValidFilePath(relativePath)) throw new IllegalArgumentException("Invalid pack path");
        try {
            Path file = root.resolve(relativePath).toRealPath();
            if (!file.startsWith(root)) throw new IllegalArgumentException("Pack path escapes directory");
            try (var stream = Files.newInputStream(file)) {
                return Optional.of(new String(stream.readNBytes(PackLayout.MAX_FILE_BYTES + 1), StandardCharsets.UTF_8));
            }
        } catch (NoSuchFileException exception) {
            return Optional.empty();
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot read pack file", exception);
        }
    }

    public String describe() { return "directory:" + root; }
}