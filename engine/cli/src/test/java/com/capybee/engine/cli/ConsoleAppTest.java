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

class ConsoleAppTest {
    private static final String MINIMAL_PACK = Path.of("..", "fixtures", "game-packs", "fixture-minimal").toString();
    private static final String DUNGEON_PACK = Path.of("..", "fixtures", "game-packs", "fixture-text-dungeon").toString();
    private static final String TEXT_INPUT_PACK = Path.of("..", "fixtures", "game-packs", "fixture-text-input").toString();

    @Test
    void helpAndMissingPackDirectoryReturnUsageErrors() {
        var help = run(new String[]{"--help"}, "");
        assertThat(help.exitCode).isZero();
        assertThat(help.output).contains("--script <file>");
        assertThat(run(new String[0], "").exitCode).isEqualTo(2);
        assertThat(run(new String[]{"--pack-dir", "missing-pack"}, "").exitCode).isEqualTo(1);
    }

    @Test
    void interactiveHelpChoiceByIdAbandonAndEofAreHandled() {
        var helpAndAbandon = run(new String[]{"--pack-dir", MINIMAL_PACK}, "?\nboat\nq\n");
        assertThat(helpAndAbandon.exitCode).isZero();
        assertThat(helpAndAbandon.output).contains(ConsoleChrome.HELP, "[abandoned]");

        var eof = run(new String[]{"--pack-dir", MINIMAL_PACK}, "");
        assertThat(eof.exitCode).isZero();
        assertThat(eof.output).contains("[eof]");
    }

    @Test
    void shortMoveAliasIsCaseInsensitiveAndMovesBeforeSceneChoiceHandling() {
        var result = run(new String[]{"--pack-dir", DUNGEON_PACK}, "M corridor\nq\n");

        assertThat(result.exitCode).isZero();
        assertThat(result.output).contains("Place: Cell", "Place: Corridor", "[abandoned]");
    }

    @Test
    void textSubmissionFailsClosedWithoutEchoAndAuthoredChoiceStillWorks() {
        var result = run(new String[]{"--pack-dir", TEXT_INPUT_PACK}, "private-marker@example.com\n1\nq\n");

        assertThat(result.exitCode).isZero();
        assertThat(result.output).contains("Text safety checks are unavailable", "You can add a thought here.", "[abandoned]");
        assertThat(result.output).doesNotContain("private-marker@example.com");
    }

    @Test
    void reportsInvalidLocaleAndMissingScript() {
        assertThat(run(new String[]{"--pack-dir", DUNGEON_PACK, "--locale", "fr"}, "").exitCode).isEqualTo(3);
        var script = run(new String[]{"--pack-dir", DUNGEON_PACK, "--script", "missing-script.txt"}, "");
        assertThat(script.exitCode).isEqualTo(2);
        assertThat(script.errors).contains("SCRIPT_INVALID");
    }

    @Test
    void invalidSnapshotIsReportedWithoutStartingASecondRun() throws Exception {
        Path snapshot = Files.createTempFile("engine-invalid-snapshot", ".json");
        try {
            Files.writeString(snapshot, "not-json", StandardCharsets.UTF_8);
            var result = run(new String[]{"--pack-dir", DUNGEON_PACK, "--load", snapshot.toString()}, "");
            assertThat(result.exitCode).isEqualTo(3);
            assertThat(result.errors).contains("SNAPSHOT_INVALID");
        } finally {
            Files.deleteIfExists(snapshot);
        }
    }

    @Test
    void mainHelpUsesInjectedConsoleUsageWithoutExiting() {
        PrintStream previous = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(bytes, true, StandardCharsets.UTF_8));
            PlayMain.main(new String[]{"--help"});
        } finally {
            System.setOut(previous);
        }
        assertThat(bytes.toString(StandardCharsets.UTF_8)).contains("Usage:");
    }

    private ConsoleResult run(String[] args, String input) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        ConsoleApp app = new ConsoleApp(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(output, true, StandardCharsets.UTF_8),
                new PrintStream(errors, true, StandardCharsets.UTF_8),
                Clock.fixed(Instant.parse("2026-10-12T10:00:00Z"), ZoneOffset.UTC),
                new SequentialIdSource("test-run"), Randoms.seeded(42));
        return new ConsoleResult(app.run(args), output.toString(StandardCharsets.UTF_8), errors.toString(StandardCharsets.UTF_8));
    }

    private record ConsoleResult(int exitCode, String output, String errors) {}
}