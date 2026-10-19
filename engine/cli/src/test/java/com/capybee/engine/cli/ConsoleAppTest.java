package com.capybee.engine.cli;

import com.capybee.engine.adapter.Randoms;
import com.capybee.engine.adapter.SequentialIdSource;
import com.capybee.engine.port.StructuredGenerationRequest;
import com.capybee.engine.port.TextGenerationResult;
import com.capybee.engine.port.TextGenerator;
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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConsoleAppTest {
    private static final String MINIMAL_PACK = Path.of("..", "fixtures", "game-packs", "fixture-minimal").toString();
    private static final String DUNGEON_PACK = Path.of("..", "fixtures", "game-packs", "fixture-text-dungeon").toString();
    private static final String TEXT_INPUT_PACK = Path.of("..", "fixtures", "game-packs", "fixture-text-input").toString();
    private static final String GENERATED_PACK = Path.of("..", "fixtures", "game-packs", "fixture-generated").toString();

    @Test
    void helpAndMissingPackDirectoryReturnUsageErrors() {
        var help = run(new String[]{"--help"}, "");
        assertThat(help.exitCode).isZero();
        assertThat(help.output).contains("--script <file>");
        assertThat(help.output).contains("--live-smoke");
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

    @Test
    void generatedCliRunsScriptAndPrintsStageOutcomesWithoutProviderBodies() throws Exception {
        Path script = Files.createTempFile("generated-play", ".txt");
        try {
            Files.writeString(script, "1\n1\n", StandardCharsets.UTF_8);
            ScriptedGenerator generator = new ScriptedGenerator(
                    "{\"title\":\"An Evening\",\"premise\":\"A lantern waits.\",\"setting\":\"Observatory\",\"beats\":[\"opening-slot\",\"meeting-slot\"]}",
                    "{\"characters\":[]}", "{\"objectives\":[]}",
                    scene("opening", "A Quiet Start", "explore", "Look around", "rest", "Rest"),
                    scene("meeting", "A New Room", "continue", "Continue", "finish", "Finish"));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ByteArrayOutputStream errors = new ByteArrayOutputStream();
            ConsoleApp app = new ConsoleApp(new ByteArrayInputStream(new byte[0]),
                    new PrintStream(output, true, StandardCharsets.UTF_8),
                    new PrintStream(errors, true, StandardCharsets.UTF_8),
                    Clock.fixed(Instant.parse("2026-10-12T10:00:00Z"), ZoneOffset.UTC),
                    new SequentialIdSource("generated-cli"), Randoms.seeded(42), generator);

            int exit = app.run(new String[]{"--pack-dir", GENERATED_PACK, "--generated", "--script", script.toString()});

            assertThat(exit).isZero();
                assertThat(output.toString(StandardCharsets.UTF_8)).contains("Generation SCENARIO GENERATED",
                    "Generation SCENE GENERATED", "provider=scripted-test");
            assertThat(output.toString(StandardCharsets.UTF_8)).contains("A Quiet Start", "A New Room");
            assertThat(output.toString(StandardCharsets.UTF_8)).doesNotContain("raw-provider-body", "secret-key");
            assertThat(errors.toString(StandardCharsets.UTF_8)).isEmpty();
        } finally {
            Files.deleteIfExists(script);
        }
    }

    @Test
    void sessionModeResumesPersistentRunAfterConsoleRestart() throws Exception {
        Path store = Files.createTempDirectory("capybee-session-store");
        Path quitScript = Files.createTempFile("session-quit", ".txt");
        Path abandonScript = Files.createTempFile("session-abandon", ".txt");
        try {
            Files.writeString(quitScript, "/quit\n", StandardCharsets.UTF_8);
            Files.writeString(abandonScript, "/abandon\n", StandardCharsets.UTF_8);
            var first = run(new String[]{"--pack-dir", MINIMAL_PACK, "--session", "--store-dir",
                    store.toString(), "--script", quitScript.toString()}, "");
            var second = run(new String[]{"--pack-dir", MINIMAL_PACK, "--session", "--store-dir",
                    store.toString(), "--script", abandonScript.toString()}, "");

            assertThat(first.exitCode).isZero();
            assertThat(second.exitCode).isZero();
            assertThat(second.output).contains("[abandoned]");
            String firstRun = first.output.lines().filter(line -> line.startsWith("Run "))
                    .findFirst().orElseThrow();
            String secondRun = second.output.lines().filter(line -> line.startsWith("Run "))
                    .findFirst().orElseThrow();
            assertThat(secondRun).isEqualTo(firstRun);
        } finally {
            Files.deleteIfExists(quitScript);
            Files.deleteIfExists(abandonScript);
            try (var paths = Files.walk(store)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    @Test
    void liveSmokeRejectsAnInjectedFakeProvider() throws Exception {
        Path script = Files.createTempFile("generated-smoke", ".txt");
        try {
            ByteArrayOutputStream errors = new ByteArrayOutputStream();
            ConsoleApp app = new ConsoleApp(new ByteArrayInputStream(new byte[0]),
                    new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                    new PrintStream(errors, true, StandardCharsets.UTF_8),
                    Clock.systemUTC(), new SequentialIdSource("smoke"), Randoms.seeded(2),
                    request -> new TextGenerationResult("{}", null, "fake", TextGenerationResult.FinishReason.COMPLETE));
            int exit = app.run(new String[]{"--pack-dir", GENERATED_PACK, "--live-smoke", "--script", script.toString()});
            assertThat(exit).isEqualTo(4);
            assertThat(errors.toString(StandardCharsets.UTF_8)).contains("LIVE_PROVIDER_NOT_CONFIGURED");
        } finally {
            Files.deleteIfExists(script);
        }
    }

    private static String scene(String sceneId, String title, String firstId, String firstLabel,
                                String secondId, String secondLabel) {
        String narration = sceneId.equals("opening") ? "Opening generated." : "Meeting generated.";
        return "{\"title\":\"" + title + "\",\"narration\":\"" + narration + "\",\"dialogue\":[],\"choices\":["
                + "{\"id\":\"" + firstId + "\",\"label\":\"" + firstLabel + "\"},"
                + "{\"id\":\"" + secondId + "\",\"label\":\"" + secondLabel + "\"}]}";
    }

    private static final class ScriptedGenerator implements TextGenerator {
        private final Deque<String> outputs = new ArrayDeque<>();
        private ScriptedGenerator(String... outputs) { this.outputs.addAll(List.of(outputs)); }
        @Override public TextGenerationResult generate(StructuredGenerationRequest request) {
            return new TextGenerationResult(outputs.removeFirst(), null, "scripted-test", TextGenerationResult.FinishReason.COMPLETE);
        }
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