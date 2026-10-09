package com.capybee.engine.pack;

import java.util.Map;
import java.util.Optional;

public record Pack(PackManifest manifest, Map<String, String> files) {
    public Pack { files = Map.copyOf(files); }

    public Optional<String> file(String relativePath) {
        return Optional.ofNullable(files.get(relativePath));
    }

    public String requireFile(String relativePath) {
        return file(relativePath).orElseThrow(() -> new IllegalStateException("Missing pack file: " + relativePath));
    }
}