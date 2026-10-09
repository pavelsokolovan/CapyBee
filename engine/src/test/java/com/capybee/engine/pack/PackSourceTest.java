package com.capybee.engine.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class PackSourceTest {
    @TempDir Path temp;

    @Test void memoryUpdatesAndCopiesAreImmutable() {
        Map<String, String> files = new HashMap<>(Map.of("a.json", "{}"));
        var original = new InMemoryPackSource(files);
        files.clear();
        assertEquals("{}", original.read("a.json").orElseThrow());
        assertEquals("new", original.with("a.json", "new").read("a.json").orElseThrow());
        assertTrue(original.without("a.json").read("a.json").isEmpty());
        assertEquals("{}", original.read("a.json").orElseThrow());
        assertEquals("memory", original.describe());
    }

    @Test void classpathReadsFixtureAndMissingFile() {
        var source = new ClasspathPackSource(getClass().getClassLoader(), "game-packs/fixture-minimal/");
        assertTrue(source.read("pack.json").isPresent());
        assertTrue(source.read("missing.json").isEmpty());
        assertEquals("classpath:game-packs/fixture-minimal", source.describe());
        assertThrows(IllegalArgumentException.class, () -> source.read("../pack.json"));
        assertThrows(IllegalArgumentException.class, () -> new ClasspathPackSource(getClass().getClassLoader(), "/bad"));
    }

    @Test void classpathReadsInsideJarWithoutDirectoryEntries() throws Exception {
        Path jar = temp.resolve("pack.jar");
        try (var output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry("packs/one/pack.json"));
            output.write("{}".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        try (var loader = new URLClassLoader(new java.net.URL[]{jar.toUri().toURL()}, null)) {
            var source = new ClasspathPackSource(loader, "packs/one");
            assertEquals("{}", source.read("pack.json").orElseThrow());
        }
    }

    @Test void directoryReadsUtf8AndRejectsPathEscapes() throws IOException {
        Files.writeString(temp.resolve("a.json"), "\u20ac");
        var source = new DirectoryPackSource(temp);
        assertEquals("\u20ac", source.read("a.json").orElseThrow());
        assertTrue(source.read("missing.json").isEmpty());
        assertTrue(source.describe().startsWith("directory:"));
        for (String path : new String[]{"../outside.json", "/absolute.json", "a\\b.json", "C:/outside.json"}) {
            assertThrows(IllegalArgumentException.class, () -> source.read(path));
        }
        assertThrows(UncheckedIOException.class, () -> new DirectoryPackSource(temp.resolve("absent")));
    }

    @Test void directoryRejectsSymlinkEscapeWhereSupported() throws IOException {
        Path root = Files.createDirectory(temp.resolve("root"));
        Path outside = Files.writeString(temp.resolve("outside.json"), "{}");
        try {
            Files.createSymbolicLink(root.resolve("link.json"), outside);
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            org.junit.jupiter.api.Assumptions.abort("Symlinks not supported by this filesystem/account");
        }
        var source = new DirectoryPackSource(root);
        assertThrows(IllegalArgumentException.class, () -> source.read("link.json"));
    }

    @Test void fileSourcesBoundReadsBeforeReturningText() throws Exception {
        Path file = temp.resolve("large.md");
        Files.writeString(file, "x".repeat(PackLayout.MAX_FILE_BYTES + 100));
        assertEquals(PackLayout.MAX_FILE_BYTES + 1, new DirectoryPackSource(temp).read("large.md").orElseThrow().length());
        try (var loader = new URLClassLoader(new java.net.URL[]{temp.toUri().toURL()}, null)) {
            Files.createDirectory(temp.resolve("packs"));
            Files.copy(file, temp.resolve("packs/large.md"));
            assertEquals(PackLayout.MAX_FILE_BYTES + 1,
                    new ClasspathPackSource(loader, "packs").read("large.md").orElseThrow().length());
        }
    }
}