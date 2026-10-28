package com.capybee.engine.cli;

import com.capybee.engine.generation.profile.ProfileLoadResult;
import com.capybee.engine.generation.profile.json.GeneratorProfileLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;

/** Bounded, non-following adapter from a local directory to the core profile loader. */
final class ProfileDirectory {
    private static final int MAX_FILES = 65;
    private static final long MAX_BYTES = 1_048_576L;

    private ProfileDirectory() {}

    static ProfileLoadResult load(Path supplied) throws IOException {
        Path root = supplied.toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) throw new IOException("PROFILE_SOURCE_INVALID");
        var resources = new LinkedHashMap<String, byte[]>();
        int count = 0;
        long total = 0;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted().toList()) {
                if (path.equals(root)) continue;
                if (Files.isSymbolicLink(path)) throw new IOException("PROFILE_SOURCE_INVALID");
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) continue;
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("PROFILE_SOURCE_INVALID");
                Path resolved = path.toAbsolutePath().normalize();
                if (!resolved.startsWith(root)) throw new IOException("PROFILE_PATH_ESCAPE");
                if (++count > MAX_FILES) throw new IOException("PROFILE_LIMIT_EXCEEDED");
                long size = Files.size(path);
                total += size;
                if (size > MAX_BYTES || total > MAX_BYTES) throw new IOException("PROFILE_LIMIT_EXCEEDED");
                resources.put(root.relativize(resolved).toString().replace('\\', '/'), Files.readAllBytes(path));
            }
        }
        return new GeneratorProfileLoader().load(resources);
    }
}
