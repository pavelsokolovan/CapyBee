package com.capybee.engine.pack;

import com.capybee.engine.generation.GeneratedGameProfile;
import java.util.Map;
import java.util.Optional;

public record Pack(PackManifest manifest, Map<String, String> files,
                   Optional<GeneratedGameProfile> generatedGameProfile) {
    public Pack { files = Map.copyOf(files); generatedGameProfile = generatedGameProfile == null ? Optional.empty() : generatedGameProfile; }

    public Pack(PackManifest manifest, Map<String, String> files) {
        this(manifest, files, Optional.empty());
    }

    public Optional<String> file(String relativePath) {
        return Optional.ofNullable(files.get(relativePath));
    }

    public String requireFile(String relativePath) {
        return file(relativePath).orElseThrow(() -> new IllegalStateException("Missing pack file: " + relativePath));
    }
}
