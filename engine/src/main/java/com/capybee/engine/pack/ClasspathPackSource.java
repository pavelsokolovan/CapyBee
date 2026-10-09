package com.capybee.engine.pack;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

public final class ClasspathPackSource implements PackSource {
    private final ClassLoader loader;
    private final String basePath;

    public ClasspathPackSource(ClassLoader loader, String basePath) {
        this.loader = Objects.requireNonNull(loader);
        String normalized = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
        if (!PackLayout.isValidFilePath(normalized + "/pack.json")) {
            throw new IllegalArgumentException("Invalid classpath root");
        }
        this.basePath = normalized;
    }

    public Optional<String> read(String relativePath) {
        if (!PackLayout.isValidFilePath(relativePath)) throw new IllegalArgumentException("Invalid pack path");
        try (var stream = loader.getResourceAsStream(basePath + "/" + relativePath)) {
            if (stream == null) return Optional.empty();
            return Optional.of(new String(stream.readNBytes(PackLayout.MAX_FILE_BYTES + 1), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot read pack resource", exception);
        }
    }

    public String describe() { return "classpath:" + basePath; }
}