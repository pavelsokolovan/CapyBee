package com.capybee.engine.pack.json;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PackIndexLintTest {
    @Test void indexesExactlyMatchPackFilesOnDisk() throws IOException {
        for (Path root : java.util.List.of(Path.of("src/main/resources/game-packs"),
                Path.of("src/test/resources/game-packs"), Path.of("../app/server/src/main/resources/game-packs"))) {
            if (!Files.exists(root)) continue;
            try (var folders = Files.list(root)) {
                for (Path folder : folders.filter(Files::isDirectory).toList()) check(folder);
            }
        }
    }

    private void check(Path folder) throws IOException {
        var manifest = JsonMapper.builder().build().readTree(Files.readString(folder.resolve("pack.json")));
        assertEquals(folder.getFileName().toString(), manifest.get("id").asString());
        var files = manifest.get("files");
        assertTrue(files.isArray());
        Set<String> indexed = new HashSet<>();
        for (int index = 0; index < files.size(); index++) {
            assertTrue(indexed.add(files.get(index).asString()), "Duplicate index in " + folder);
        }
        try (var paths = Files.walk(folder)) {
            Set<String> actual = new HashSet<>();
            paths.filter(Files::isRegularFile).forEach(path -> actual.add(folder.relativize(path).toString().replace('\\', '/')));
            actual.remove("pack.json");
            assertEquals(actual, indexed, folder.toString());
        }
    }
}