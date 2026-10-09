package com.capybee.engine.pack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class InMemoryPackSource implements PackSource {
    private final Map<String, String> files;

    public InMemoryPackSource(Map<String, String> files) { this.files = Map.copyOf(files); }
    public Optional<String> read(String relativePath) { return Optional.ofNullable(files.get(relativePath)); }
    public String describe() { return "memory"; }

    public InMemoryPackSource with(String path, String content) {
        Map<String, String> updated = new LinkedHashMap<>(files);
        updated.put(path, content);
        return new InMemoryPackSource(updated);
    }

    public InMemoryPackSource without(String path) {
        Map<String, String> updated = new LinkedHashMap<>(files);
        updated.remove(path);
        return new InMemoryPackSource(updated);
    }
}