package com.capybee.server.game;

import com.capybee.engine.generation.profile.ValidatedGeneratorProfile;
import com.capybee.engine.generation.profile.json.GeneratorProfileLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.io.InputStream;

/** Deployment-owned, immutable allowlist of validated generator profiles. */
public final class GeneratorProfileCatalog {
    private static final int MAX_FILES = 65;
    private static final long MAX_BYTES = 1_048_576;
    private final boolean enabled;
    private final Map<String, ValidatedGeneratorProfile> profiles;

    public GeneratorProfileCatalog(boolean enabled, Path root, List<String> ids) {
        this.enabled = enabled;
        var loaded = new LinkedHashMap<String, ValidatedGeneratorProfile>();
        if (!enabled) { profiles = Map.of(); return; }
        if (ids == null || ids.isEmpty()) throw new IllegalStateException("DYNAMIC_PROFILE_REQUIRED");
        try {
            Path canonicalRoot = root == null ? null : root.toRealPath(LinkOption.NOFOLLOW_LINKS);
            for (String id : ids) {
                if (id == null || !id.matches("[a-z][a-z0-9-]{0,63}") || loaded.containsKey(id))
                    throw new IllegalStateException("DYNAMIC_PROFILE_INVALID");
                var files = canonicalRoot == null ? classpathProfile(id) : directoryProfile(canonicalRoot, id);
                var result = new GeneratorProfileLoader().load(files);
                var profile = result.profile().orElseThrow(() -> new IllegalStateException("DYNAMIC_PROFILE_INVALID"));
                if (!profile.identity().id().value().equals(id)) throw new IllegalStateException("DYNAMIC_PROFILE_INVALID");
                loaded.put(id, profile);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("DYNAMIC_PROFILE_INVALID", failure);
        }
        profiles = Map.copyOf(loaded);
    }

    public boolean enabled() { return enabled; }
    public List<ValidatedGeneratorProfile> all() { return List.copyOf(profiles.values()); }
    public Optional<ValidatedGeneratorProfile> find(String id) { return Optional.ofNullable(profiles.get(id)); }

    private static Map<String, byte[]> directoryProfile(Path canonicalRoot, String id) throws IOException {
        Path directory = canonicalRoot.resolve(id).normalize();
        Path real = directory.toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (!real.startsWith(canonicalRoot) || Files.isSymbolicLink(directory))
            throw new IllegalStateException("DYNAMIC_PROFILE_INVALID");
        var files = new LinkedHashMap<String, byte[]>(); long total = 0;
        try (var paths = Files.walk(real)) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) {
                if (Files.isSymbolicLink(file)) throw new IllegalStateException("DYNAMIC_PROFILE_INVALID");
                String relative = real.relativize(file).toString().replace('\\', '/');
                byte[] bytes = Files.readAllBytes(file); total += bytes.length;
                if (files.size() >= MAX_FILES || total > MAX_BYTES) throw new IllegalStateException("DYNAMIC_PROFILE_LIMIT_EXCEEDED");
                files.put(relative, bytes);
            }
        }
        return files;
    }

    private static Map<String, byte[]> classpathProfile(String id) throws IOException {
        if (!"generic-adventure".equals(id)) throw new IllegalStateException("DYNAMIC_PROFILE_INVALID");
        var files = new LinkedHashMap<String, byte[]>(); long total = 0;
        for (String relative : List.of("profile.json", "setup.json", "prompts/en/blueprint.md",
                "prompts/pl/blueprint.md", "quality/en.json", "quality/pl.json",
                "strings/en.json", "strings/pl.json")) {
            String resource = "release/profiles/" + id + "/" + relative;
            try (InputStream input = GeneratorProfileCatalog.class.getClassLoader().getResourceAsStream(resource)) {
                if (input == null) throw new IllegalStateException("DYNAMIC_PROFILE_INVALID");
                byte[] bytes = input.readNBytes((int) MAX_BYTES + 1); total += bytes.length;
                if (total > MAX_BYTES) throw new IllegalStateException("DYNAMIC_PROFILE_LIMIT_EXCEEDED");
                files.put(relative, bytes);
            }
        }
        return files;
    }
}
