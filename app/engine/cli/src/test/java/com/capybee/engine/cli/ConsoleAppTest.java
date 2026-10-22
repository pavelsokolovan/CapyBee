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
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
        void sessionCommandsRenderStatusEventsAndDebugWithoutEndingTheRun() {
        var result = run(new String[]{"--pack-dir", DUNGEON_PACK, "--session", "--prefetch", "off",
                "--calls-per-day", "0", "--run-calls", "5"},
            "/help\n/status\n/events\n/events 0\n/events invalid\n/debug on\n/debug off\n"
                + "/unknown\nunknown-option\n999\n\n/move corridor\n/text private-marker@example.com\n/quit\n");

        assertThat(result.exitCode).isZero();
        assertThat(result.errors).isEmpty();
        assertThat(result.output).contains("/debug on|off", "! Unknown command. Type ? for help.",
            "! Choose an option, or press Enter to continue.", "revision=", "source=NONE latency=0ms",
            "calls=0/5", "prefetch=ready:0 inflight:0 calls:0", "Place: Cell", "Place: Corridor",
            "COMMAND_NOT_ALLOWED", "INPUT_NOT_EXPECTED");
        assertThat(result.output).doesNotContain("[abandoned]", "private-marker@example.com");
        assertThat(result.output.lines().filter(line -> line.contains("revision="))).hasSize(1);
        String diagnostics = result.output.lines().filter(line -> line.contains("revision=")).findFirst()
            .orElseThrow();
        if (diagnostics.startsWith("> ")) diagnostics = diagnostics.substring(2);
        assertThat(diagnostics).matches("revision=\\d+ source=(NONE|PREFETCHED|JOINED_PREFETCH|INLINE_GENERATED|INLINE_REPAIRED|FALLBACK) "
            + "latency=\\d+ms calls=\\d+/\\d+ prefetch=ready:\\d+ inflight:\\d+ calls:\\d+ "
            + "hits:\\d+ misses:\\d+ wasted:\\d+ budget=\\d+/\\d+");
        }

    @Test
    void slowSessionCommandUsesInjectedPollingAndPrintsThinkingOnce() throws Exception {
        Path script = Files.createTempFile("slow-session", ".txt");
        CountDownLatch openingGenerated = new CountDownLatch(1);
        CountDownLatch meetingEntered = new CountDownLatch(1);
        CountDownLatch releaseMeeting = new CountDownLatch(1);
        AtomicInteger meetingCalls = new AtomicInteger();
        java.util.ArrayList<Duration> sleeps = new java.util.ArrayList<>();
        try {
            Files.writeString(script, "/debug on\n1\n/quit\n", StandardCharsets.UTF_8);
            TextGenerator generator = request -> {
                String output;
                if (request.slotKey() == null) {
                    output = switch (request.taskId()) {
                        case "GEN_SCENARIO" -> "{\"title\":\"An Evening\",\"premise\":\"A lantern waits.\",\"setting\":\"Observatory\",\"beats\":[\"opening-slot\",\"meeting-slot\"]}";
                        case "GEN_CAST" -> "{\"characters\":[]}";
                        case "GEN_QUEST" -> "{\"objectives\":[]}";
                        default -> "{}";
                    };
                } else if (request.slotKey().equals("opening-slot")) {
                    openingGenerated.countDown();
                    output = scene("opening", "A Quiet Start", "explore", "Look around", "rest", "Rest");
                } else {
                    meetingCalls.incrementAndGet();
                    meetingEntered.countDown();
                    awaitGate(releaseMeeting);
                    output = scene("meeting", "A New Room", "continue", "Continue", "finish", "Finish");
                }
                return new TextGenerationResult(output, null, "test-provider",
                        TextGenerationResult.FinishReason.COMPLETE);
            };
            Sleeper sleeper = delay -> {
                sleeps.add(delay);
                try {
                    if (openingGenerated.getCount() > 0) openingGenerated.await(10, TimeUnit.SECONDS);
                    if (meetingEntered.getCount() == 0) releaseMeeting.countDown();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            };
                ConsoleTuning tuning = new ConsoleTuning(Duration.ofSeconds(8), Duration.ofMillis(10),
                    Duration.ofMillis(30), Duration.ofSeconds(30), sleeper);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ByteArrayOutputStream errors = new ByteArrayOutputStream();
            ConsoleApp app = new ConsoleApp(new ByteArrayInputStream(new byte[0]),
                    new PrintStream(output, true, StandardCharsets.UTF_8),
                    new PrintStream(errors, true, StandardCharsets.UTF_8),
                    Clock.fixed(Instant.parse("2026-10-12T10:00:00Z"), ZoneOffset.UTC),
                    new SequentialIdSource("slow-session"), Randoms.seeded(42), generator, tuning);

                int exit = app.run(new String[]{"--pack-dir", GENERATED_PACK, "--generated", "--session",
                        "--prefetch", "off", "--script", script.toString()});

            String transcript = output.toString(StandardCharsets.UTF_8);
            assertThat(exit).as("output=%s errors=%s", transcript, errors.toString(StandardCharsets.UTF_8)).isZero();
            assertThat(transcript.lines().filter(line -> line.equals("CapyBee is thinking…"))).hasSize(1);
                assertThat(transcript).contains("A New Room", "source=INLINE_GENERATED latency=0ms",
                    "prefetch=ready:0 inflight:0 calls:0").doesNotContain("SESSION_UNAVAILABLE");
            assertThat(errors.toString(StandardCharsets.UTF_8)).isEmpty();
            assertThat(meetingCalls).hasValue(1);
            assertThat(sleeps).isNotEmpty().allSatisfy(delay -> assertThat(delay).isEqualTo(Duration.ofMillis(30)));
        } finally {
            releaseMeeting.countDown();
            Files.deleteIfExists(script);
        }
    }

        @Test
        void interactiveSessionAbandonCanBeCancelledThenConfirmed() {
        var result = run(new String[]{"--pack-dir", MINIMAL_PACK, "--session"},
            "q\nn\n/abandon\nyes\n");

        assertThat(result.exitCode).isZero();
        assertThat(result.errors).isEmpty();
        assertThat(result.output).contains("Abandon this run for good? (y/N)", "Cancelled.", "[abandoned]");
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

    @Test
    void canonicalPlayShowsPackCardAndFocusedPlayerScreens() {
        var result = run(new String[]{"play", "--pack-dir", DUNGEON_PACK, "--memory"},
                "\n/help\n/look\n/status\n/objectives\n/characters\n/map\n/save\nq\n/quit\n");

        assertThat(result.exitCode).isZero();
        assertThat(result.errors).isEmpty();
        assertThat(result.output).contains("CapyBee", "Pack: fixture-text-dungeon", "Saving: temporary",
                "Press Enter to set up this game", "/objectives", "Status", "Objectives", "Characters", "Map",
                "This run is temporary", "QUIT_REQUIRES_SLASH");
        assertThat(result.output).doesNotContain("[abandoned]");
    }

    @Test
    void canonicalScriptRequiresExplicitRunChoiceAndCanResume() throws Exception {
        Path store = Files.createTempDirectory("capybee-play-store");
        Path script = Files.createTempFile("play-quit", ".txt");
        try {
            Files.writeString(script, "/quit\n", StandardCharsets.UTF_8);
            String[] base = {"play", "--pack-dir", MINIMAL_PACK, "--store-dir", store.toString(),
                    "--script", script.toString()};
            var ambiguous = run(base, "");
            assertThat(ambiguous.exitCode).isZero();
            assertThat(ambiguous.output).contains("Saving: on");

            var unresolved = run(base, "");
            assertThat(unresolved.exitCode).isEqualTo(2);
            assertThat(unresolved.errors).contains("STARTUP_AMBIGUOUS");

            String[] resume = java.util.Arrays.copyOf(base, base.length + 1);
            resume[base.length] = "--continue";
            var continued = run(resume, "");
            assertThat(continued.exitCode).isZero();
            assertThat(continued.output).contains("Run ", "> /quit");
        } finally {
            Files.deleteIfExists(script);
            try (var paths = Files.walk(store)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    @Test
    void strictScriptEofRequiresACompletedRun() throws Exception {
        Path script = Files.createTempFile("play-incomplete", ".txt");
        try {
            Files.writeString(script, "", StandardCharsets.UTF_8);

            var result = run(new String[]{"play", "--pack-dir", MINIMAL_PACK, "--memory",
                    "--script", script.toString()}, "");

            assertThat(result.exitCode).isEqualTo(4);
            assertThat(result.errors).contains("SCRIPT_INCOMPLETE");
        } finally {
            Files.deleteIfExists(script);
        }
    }

    @Test
    void strictScriptRejectsAnUnavailableChoiceImmediately() throws Exception {
        Path script = Files.createTempFile("play-rejected", ".txt");
        try {
            Files.writeString(script, "not-an-option\n/quit\n", StandardCharsets.UTF_8);

            var result = run(new String[]{"play", "--pack-dir", MINIMAL_PACK, "--memory",
                    "--script", script.toString()}, "");

            assertThat(result.exitCode).isEqualTo(4);
            assertThat(result.errors).contains("SCRIPT_COMMAND_REJECTED");
            assertThat(result.output).doesNotContain("> /quit");
        } finally {
            Files.deleteIfExists(script);
        }
    }

    @Test
    void strictScriptCannotQuitWhileACommandIsPending() throws Exception {
        Path script = Files.createTempFile("play-pending", ".txt");
        CountDownLatch release = new CountDownLatch(1);
        try {
            Files.writeString(script, "1\n/quit\n", StandardCharsets.UTF_8);
            TextGenerator generator = request -> {
                String output;
                if (request.slotKey() == null) {
                    output = switch (request.taskId()) {
                        case "GEN_SCENARIO" -> "{\"title\":\"An Evening\",\"premise\":\"A lantern waits.\",\"setting\":\"Observatory\",\"beats\":[\"opening-slot\",\"meeting-slot\"]}";
                        case "GEN_CAST" -> "{\"characters\":[]}";
                        case "GEN_QUEST" -> "{\"objectives\":[]}";
                        default -> "{}";
                    };
                } else if (request.slotKey().equals("opening-slot")) {
                    output = scene("opening", "A Quiet Start", "explore", "Look around", "rest", "Rest");
                } else {
                    awaitGate(release);
                    output = scene("meeting", "A New Room", "continue", "Continue", "finish", "Finish");
                }
                return new TextGenerationResult(output, null, "test-provider",
                        TextGenerationResult.FinishReason.COMPLETE);
            };
            ConsoleTuning tuning = new ConsoleTuning(Duration.ofSeconds(1), Duration.ofMillis(1),
                    Duration.ofMillis(1), Duration.ZERO, ignored -> { });
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ByteArrayOutputStream errors = new ByteArrayOutputStream();
            ConsoleApp app = new ConsoleApp(ByteArrayInputStream.nullInputStream(),
                    new PrintStream(output, true, StandardCharsets.UTF_8),
                    new PrintStream(errors, true, StandardCharsets.UTF_8),
                    Clock.fixed(Instant.parse("2026-10-12T10:00:00Z"), ZoneOffset.UTC),
                    new SequentialIdSource("pending-script"), Randoms.seeded(42), generator, tuning);

            int exit = app.run(new String[]{"play", "--pack-dir", GENERATED_PACK, "--generated", "--memory",
                    "--prefetch", "off", "--script", script.toString()});

            assertThat(exit).isEqualTo(4);
            assertThat(errors.toString(StandardCharsets.UTF_8)).contains("SCRIPT_COMMAND_PENDING");
        } finally {
            release.countDown();
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

    private static void awaitGate(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test gate timed out");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("test gate interrupted", interrupted);
        }
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
