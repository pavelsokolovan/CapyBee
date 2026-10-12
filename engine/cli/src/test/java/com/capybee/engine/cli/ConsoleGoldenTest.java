package com.capybee.engine.cli;

import com.capybee.engine.adapter.Randoms;
import com.capybee.engine.adapter.SequentialIdSource;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ConsoleGoldenTest {
    @Test
    void winningScriptMatchesGoldenTranscript() throws Exception {
        assertScriptMatches("dungeon-win.txt", "dungeon-win.txt");
    }

    @Test
    void lockedExitScriptMatchesGoldenTranscriptAndEndsWithoutEofMarker() throws Exception {
        assertScriptMatches("dungeon-locked-exit.txt", "dungeon-locked-exit.txt");
    }

    private void assertScriptMatches(String scriptName, String goldenName) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        PrintStream errors = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
        ConsoleApp app = new ConsoleApp(new ByteArrayInputStream(new byte[0]), output, errors,
                Clock.fixed(Instant.parse("2026-10-12T10:00:00Z"), ZoneOffset.UTC),
                new SequentialIdSource("run"), Randoms.seeded(42));
        String packPath = Path.of("..", "fixtures", "game-packs", "fixture-text-dungeon").toString();
        String scriptPath = Path.of("src", "test", "resources", "scripts", scriptName).toString();

        assertThat(app.run(new String[]{"--pack-dir", packPath, "--script", scriptPath})).isZero();
        String actual = bytes.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
        String expected = Files.readString(Path.of("src", "test", "resources", "golden", goldenName), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
        assertThat(actual).isEqualTo(expected + (expected.endsWith("\n") ? "" : "\n"));
        assertThat(actual).doesNotContain("[eof]");
    }
}