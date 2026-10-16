package com.capybee.engine.pack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class PackLayout {
    public static final Set<Integer> SUPPORTED_SCHEMA_VERSIONS = Set.of(1);
    public static final int MAX_FILE_BYTES = 256 * 1024;
    public static final int MAX_PACK_BYTES = 2 * 1024 * 1024;
    public static final int MAX_FREE_TEXT_CHARS = 500;
    public static final int MAX_FREE_TEXT_INPUTS = 1;

    private PackLayout() {}

    public static List<String> requiredFiles(PackManifest manifest) {
        return requiredFiles(manifest.locales(), manifest.features());
    }

    public static List<String> requiredFiles(List<LocaleTag> locales, Set<PackFeature> features) {
        List<String> paths = new ArrayList<>();
        for (LocaleTag locale : locales) {
            paths.add("strings/" + locale.value() + ".json");
            if (features.contains(PackFeature.GENERATION)) {
                paths.add("prompts/" + locale.value() + "/system.md");
            }
        }
        if (features.contains(PackFeature.GENERATION)) {
            paths.add("generation/catalog.json");
            paths.add("generation/templates.json");
        }
        if (features.contains(PackFeature.PLACES)) paths.add("places.json");
        return List.copyOf(paths);
    }

    public static boolean isValidFilePath(String path) {
        if (path == null || path.isEmpty() || path.length() > 120 || path.startsWith("/")
                || path.contains("\\") || path.contains("..") || path.contains(":")) return false;
        if (path.chars().anyMatch(character -> character < 32 || character == 127)) return false;
        if (!path.endsWith(".json") && !path.endsWith(".md")) return false;
        for (String part : path.split("/", -1)) {
            if (part.isEmpty() || part.equals(".")) return false;
        }
        return true;
    }
}