package com.capybee.engine.cli;

import com.capybee.engine.session.SessionTypes;
import com.capybee.engine.session.adapter.LocalSessionStore;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Deterministic, credential-free dynamic release route with a real two-JVM restart. */
final class DynamicCertificationCommand {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final PrintStream out;
    private final PrintStream err;
    private final Clock clock;

    DynamicCertificationCommand(PrintStream out, PrintStream err, Clock clock) {
        this.out = out; this.err = err; this.clock = clock;
    }

    int run(String[] args) {
        Path profile = null, fixture = null, evidence = null;
        try {
            for (int i = 1; i < args.length; i++) switch (args[i]) {
                case "--dynamic" -> { }
                case "--mode" -> { if (!"offline".equals(next(args, ++i))) throw new IllegalArgumentException(); }
                case "--profile-dir" -> profile = Path.of(next(args, ++i)).toAbsolutePath().normalize();
                case "--fixture-dir" -> fixture = Path.of(next(args, ++i)).toAbsolutePath().normalize();
                case "--evidence-dir" -> evidence = Path.of(next(args, ++i)).toAbsolutePath().normalize();
                default -> throw new IllegalArgumentException();
            }
            if (profile == null || fixture == null || evidence == null || !ProfileDirectory.load(profile).valid()
                    || !Files.isRegularFile(fixture.resolve("blueprint.json"))) throw new IllegalArgumentException();
        } catch (Exception invalid) { err.println("CERTIFICATION_ARGUMENT_INVALID"); return 2; }

        UUID certificationId = UUID.randomUUID();
        Path work = null;
        try {
            Files.createDirectories(evidence);
            work = Files.createTempDirectory("capybee-dynamic-cert-");
            Path store = work.resolve("store"), firstScript = work.resolve("first.script"), secondScript = work.resolve("second.script");
            Files.writeString(firstScript, "\ny\n1\n1\n1\n/save\n/quit\n", StandardCharsets.UTF_8);
            Files.writeString(secondScript, "1\n1\n1\n", StandardCharsets.UTF_8);
            List<String> first = List.of("play", "--dynamic", "--profile-dir", profile.toString(), "--script",
                    firstScript.toString(), "--store-dir", store.toString(), "--new", "--no-color");
            ProcessResult a = child(first, Map.of("GENERATION_PROVIDER_MODE", "certification",
                    "CAPYBEE_CERTIFICATION_FIXTURE_DIR", fixture.toString()));
            if (a.exit != 0) throw new IllegalStateException("PROCESS_A_FAILED");
            SnapshotEvidence checkpoint = inspect(store, false);
            List<String> second = List.of("play", "--dynamic", "--continue", "--offline", "--script",
                    secondScript.toString(), "--store-dir", store.toString(), "--no-color");
            ProcessResult b = child(second, Map.of("GENERATION_PROVIDER_MODE", "disabled"));
            if (b.exit != 0 || a.pid == b.pid) throw new IllegalStateException("PROCESS_B_FAILED");
            SnapshotEvidence finished = inspect(store, true);
            if (!checkpoint.runId.equals(finished.runId) || !checkpoint.gameFingerprint.equals(finished.gameFingerprint)
                    || !checkpoint.definitionFingerprint.equals(finished.definitionFingerprint)
                    || checkpoint.totalProviderCalls != finished.totalProviderCalls
                    || finished.revision != 6) throw new IllegalStateException("RESTART_EVIDENCE_INVALID");
            ObjectNode root = JSON.createObjectNode();
            root.put("schemaVersion", 1).put("sourceKind", "DYNAMIC").put("suiteId", "dynamic-v1")
                    .put("certificationId", certificationId.toString()).put("result", "PASS")
                    .put("runId", finished.runId.toString()).put("checkpointRevision", checkpoint.revision)
                    .put("finalRevision", finished.revision).put("restartProcesses", 2)
                    .put("processA", a.pid).put("processB", b.pid)
                    .put("gameFingerprint", finished.gameFingerprint)
                    .put("definitionFingerprint", finished.definitionFingerprint)
                    .put("resultClass", finished.resultClass)
                    .put("providerCallsAtCheckpoint", checkpoint.totalProviderCalls)
                    .put("providerCallsAtCompletion", finished.totalProviderCalls)
                    .put("blueprintCallsAfterResume", finished.totalProviderCalls - checkpoint.totalProviderCalls);
            Path target = evidence.resolve(certificationId + ".json");
            Path temporary = evidence.resolve(certificationId + ".json.tmp");
            Files.writeString(temporary, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(root), StandardCharsets.UTF_8);
            Files.move(temporary, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            out.println("certify source=dynamic result=PASS evidence-id=" + certificationId);
            deleteTree(work); return 0;
        } catch (Exception failure) {
            err.println("DYNAMIC_CERTIFICATION_FAILED");
            return 5;
        }
    }

    private ProcessResult child(List<String> args, Map<String,String> environment) throws Exception {
        Path java = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java");
        List<String> command = new ArrayList<>(List.of(java.toString(), "-Dfile.encoding=UTF-8", "-cp",
                System.getProperty("java.class.path"), PlayMain.class.getName())); command.addAll(args);
        ProcessBuilder builder = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().keySet().removeIf(k -> k.toUpperCase(Locale.ROOT).contains("ANTHROPIC")
                || k.toUpperCase(Locale.ROOT).startsWith("GENERATION_PROVIDER") || k.equals("CAPYBEE_CERTIFICATION_FIXTURE_DIR"));
        builder.environment().putAll(environment);
        Process process = builder.start(); long pid = process.pid();
        if (!process.waitFor(Duration.ofMinutes(2).toMillis(), TimeUnit.MILLISECONDS)) {
            process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); throw new IllegalStateException("TIMEOUT");
        }
        return new ProcessResult(pid, process.exitValue());
    }

    private SnapshotEvidence inspect(Path store, boolean ended) {
        try (LocalSessionStore opened = LocalSessionStore.open(store, clock)) {
            var page = opened.list(new SessionTypes.OwnerRef("local-console"), SessionTypes.RunQuery.visible(), null, 10);
            if (page.runs().size() != 1) throw new IllegalStateException();
            var run = opened.find(page.runs().getFirst().runId(), new SessionTypes.OwnerRef("local-console")).orElseThrow();
            if (run.snapshot() == null || !run.snapshot().dynamicGame() || run.snapshot().state().ended() != ended) throw new IllegalStateException();
            var bundle = run.snapshot().dynamic().bundle();
            String result = run.snapshot().state().outcome() == null ? "IN_PROGRESS"
                    : bundle.definition().runtimeRules().outcomes().stream().filter(v -> v.id().equals(run.snapshot().state().outcome()))
                    .findFirst().orElseThrow().resultClass().name();
            return new SnapshotEvidence(run.id(), run.revision(), bundle.gameFingerprint(), bundle.definitionFingerprint(),
                    result, run.snapshot().dynamic().totalProviderCalls());
        }
    }

    private static String next(String[] args, int index) { if (index >= args.length) throw new IllegalArgumentException(); return args[index]; }
    private static void deleteTree(Path root) throws Exception { if (root == null || !Files.exists(root)) return; try (var paths=Files.walk(root)){for(Path path:paths.sorted(java.util.Comparator.reverseOrder()).toList())Files.deleteIfExists(path);} }
    private record ProcessResult(long pid, int exit) {}
    private record SnapshotEvidence(UUID runId, long revision, String gameFingerprint, String definitionFingerprint,
                                    String resultClass, int totalProviderCalls) {}
}
