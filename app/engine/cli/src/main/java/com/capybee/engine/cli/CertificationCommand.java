package com.capybee.engine.cli;

import com.capybee.engine.adapter.Randoms;
import com.capybee.engine.adapter.UuidIdSource;
import com.capybee.engine.adapter.anthropic.AnthropicConfig;
import com.capybee.engine.adapter.anthropic.AnthropicTextGenerator;
import com.capybee.engine.port.StructuredGenerationRequest;
import com.capybee.engine.port.TextGenerationResult;
import com.capybee.engine.port.TextGenerator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import com.capybee.engine.session.SessionTypes;
import com.capybee.engine.session.adapter.LocalSessionStore;

/** Release-only certification entry point. It deliberately keeps game prose out of its output and evidence. */
final class CertificationCommand {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> ROOT_FIELDS = Set.of("schemaVersion", "suiteId", "pack", "locale",
            "prefetch", "freeText", "routes", "resumeCheckpoint", "requiredStages");
    private static final Set<String> PRICE_FIELDS = Set.of("schemaVersion", "providerId", "modelId", "currency",
            "effectiveAt", "reviewedAt", "inputUsdPerMillionTokens", "outputUsdPerMillionTokens",
            "cacheReadUsdPerMillionTokens", "cacheWriteUsdPerMillionTokens", "reservedMaximumUsdPerCall",
            "sourceLabel", "freeTier");
    private final PrintStream out;
    private final PrintStream err;
    private final Clock clock;

    CertificationCommand(PrintStream out, PrintStream err, Clock clock) {
        this.out = out;
        this.err = err;
        this.clock = clock;
    }

    int run(String[] arguments) {
        Options options;
        try {
            options = Options.parse(arguments);
            if (options.help) { out.println(usage()); return 0; }
        } catch (CertificationFailure failure) {
            err.println(failure.code + ": " + failure.getMessage());
            return 2;
        }
        UUID certificationId = UUID.randomUUID();
        Instant started = clock.instant();
        Path incomplete = options.evidenceDir.resolve(certificationId + ".incomplete").toAbsolutePath().normalize();
        try {
            preflight(options, incomplete);
            Suite suite = Suite.read(options.suite, options.packDir);
            Pricing pricing = options.live ? Pricing.read(options.pricingFile, clock.instant()) : null;
            TextGenerator provider = null;
            ObservingGenerator observed = null;
            if (options.live) {
                AnthropicConfig config = AnthropicConfig.fromEnvironment();
                if (!config.modelId().equals(pricing.modelId)) fail("PRICING_MODEL_MISMATCH", "Pricing model must match GENERATION_MODEL_ID.");
                observed = new ObservingGenerator(new AnthropicTextGenerator(config), config.modelId(), pricing,
                        options.maxCalls, options.maxCost);
                provider = observed;
            }
            Files.createDirectories(incomplete.resolve("routes"));
            List<RouteResult> results = new ArrayList<>();
            for (Route route : suite.routes) {
                out.println("certify route=" + route.id + " state=RUNNING");
                RouteResult result = executeRoute(options, suite, route, provider);
                results.add(result);
                writeJson(incomplete.resolve("routes").resolve(route.id + ".json"), result.json());
                out.println("certify route=" + route.id + " state=" + (result.passed ? "PASS" : "FAIL"));
                if (!result.passed && !options.continueAfterFailure) break;
            }
            boolean passed = results.size() == suite.routes.size() && results.stream().allMatch(r -> r.passed);
            if (observed != null && observed.failureCode != null) passed = false;
            writeEvidence(options, suite, pricing, observed, results, incomplete, certificationId, started, passed);
            Path finalized = options.evidenceDir.resolve(certificationId.toString()).toAbsolutePath().normalize();
            moveAtomically(incomplete, finalized);
            out.println("certify result=" + (passed ? "PASS" : "FAIL") + " evidence-id=" + certificationId);
            return passed ? 0 : 5;
        } catch (CertificationFailure failure) {
            err.println(failure.code + ": " + failure.getMessage());
            safeFailureEvidence(incomplete, certificationId, started, failure);
            return failure.exit;
        } catch (Exception failure) {
            err.println("CERTIFICATION_LOCAL_FAILURE: certification could not complete.");
            safeFailureEvidence(incomplete, certificationId, started,
                    new CertificationFailure("CERTIFICATION_LOCAL_FAILURE", "Local certification failure.", 3));
            return 3;
        }
    }

    private void preflight(Options options, Path incomplete) {
        if (!Files.isDirectory(options.packDir)) fail("PACK_SOURCE_INVALID", "Pack directory is missing.");
        if (!Files.isRegularFile(options.suite)) fail("SUITE_INVALID", "Suite file is missing.");
        if (Files.exists(incomplete)) fail("EVIDENCE_EXISTS", "Evidence target already exists.");
        if (options.live) {
            if (PlayMain.ProviderMode.fromEnvironment() != PlayMain.ProviderMode.CERTIFICATION)
                fail("PROVIDER_MODE_INVALID", "GENERATION_PROVIDER_MODE must be certification for live mode.");
            if (Boolean.parseBoolean(System.getenv("ANTHROPIC_DEBUG")))
                fail("DEBUG_LOGGING_FORBIDDEN", "Provider debug logging is forbidden in certification.");
            if (!options.confirm) fail("LIVE_CONFIRMATION_REQUIRED", "Use --confirm-live-provider-cost after reviewing limits.");
            if (options.maxCalls == null || options.maxCost == null || options.pricingFile == null)
                fail("LIVE_LIMIT_REQUIRED", "Live mode requires pricing, call, and cost limits.");
        }
        try { Files.createDirectories(options.evidenceDir.toAbsolutePath().normalize()); }
        catch (Exception e) { fail("EVIDENCE_UNWRITABLE", "Evidence directory is not writable.", 6); }
    }

    private RouteResult executeRoute(Options options, Suite suite, Route route, TextGenerator provider) throws Exception {
        Path store = Files.createTempDirectory("engine-cert-" + route.id + "-");
        try {
            List<String> lines = Files.readAllLines(route.script, StandardCharsets.UTF_8);
            int quit = indexOfQuit(lines);
            boolean restart = route.id.equals(suite.resumeRoute);
            if (restart && quit < 0) fail("SUITE_INVALID", "Resume route must contain /quit.");
            List<String> before = restart ? lines.subList(0, quit + 1) : lines;
            List<String> after = restart ? lines.subList(quit + 1, lines.size()) : List.of();
            int first = invokePlay(options, suite, route, provider, store, before, false);
            Long checkpointRevision = null;
            if (restart && first == 0) {
                ObservedRun checkpoint = inspect(store, options.packDir);
                checkpointRevision = checkpoint.revision;
                if (!"ACTIVE".equals(checkpoint.status) || checkpoint.revision != 4
                        || checkpoint.acceptedChoices != 4 || checkpoint.pendingCommands != 0) {
                    return checkpoint.result(route.id, false, 1, checkpointRevision);
                }
            }
            int second = restart && first == 0
                    ? invokePlay(options, suite, route, provider, store, after, true) : 0;
            ObservedRun observed = inspect(store, options.packDir);
            boolean passed = first == 0 && second == 0 && "ENDED".equals(observed.status)
                    && observed.revision == 8 && observed.acceptedChoices == 8
                    && observed.pendingCommands == 0 && route.expectedResult.equals(observed.resultClass);
            return observed.result(route.id, passed, restart ? 2 : 1, checkpointRevision);
        } finally {
            deleteTree(store);
        }
    }

    private int invokePlay(Options certification, Suite suite, Route route, TextGenerator provider, Path store,
                           List<String> commands, boolean resume) throws Exception {
        Path script = Files.createTempFile("engine-cert-script-", ".txt");
        Files.write(script, commands, StandardCharsets.UTF_8);
        List<String> args = new ArrayList<>(List.of("play", "--pack-dir", certification.packDir.toString(),
                "--script", script.toString(), "--prefetch", "off", "--no-color"));
        args.addAll(List.of("--store-dir", store.toString()));
        if (resume) args.add("--continue");
        else {
            args.addAll(List.of("--new", "--generated", "--accept-generation-disclosure"));
            if (!certification.live) args.add("--offline");
            preset(route.id).forEach((key, value) -> args.addAll(List.of("--input", key + "=" + value)));
        }
        if (!certification.live && provider == null) {
            int exit = invokeChild(args);
            Files.deleteIfExists(script);
            return exit;
        }
        ByteArrayOutputStream discardedOut = new ByteArrayOutputStream();
        ByteArrayOutputStream discardedErr = new ByteArrayOutputStream();
        ConsoleTuning certificationTuning = new ConsoleTuning(Duration.ofSeconds(30), Duration.ofMillis(250),
                Duration.ofSeconds(5), Duration.ofMinutes(10), Sleeper.system());
        int exit = new ConsoleApp(ByteArrayInputStream.nullInputStream(), new PrintStream(discardedOut),
                new PrintStream(discardedErr), clock, new UuidIdSource(), Randoms.seeded(27_013L), provider,
                certificationTuning, ConsolePaths.system(), null)
                .run(args.toArray(String[]::new));
        Files.deleteIfExists(script);
        return exit;
    }

    private int invokeChild(List<String> args) throws Exception {
        Path javaHome = Path.of(System.getProperty("java.home"));
        Path executable = javaHome.resolve("bin").resolve(System.getProperty("os.name").toLowerCase(Locale.ROOT)
                .contains("win") ? "java.exe" : "java");
        List<String> command = new ArrayList<>(List.of(executable.toString(), "-Dfile.encoding=UTF-8",
                "-cp", System.getProperty("java.class.path"), PlayMain.class.getName()));
        command.addAll(args);
        Path stdout = Files.createTempFile("engine-cert-child-out-", ".txt");
        Path stderr = Files.createTempFile("engine-cert-child-err-", ".txt");
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectOutput(stdout.toFile()).redirectError(stderr.toFile());
            Map<String, String> environment = builder.environment();
            environment.keySet().removeIf(key -> key.toUpperCase(Locale.ROOT).contains("ANTHROPIC")
                    || key.toUpperCase(Locale.ROOT).startsWith("GENERATION_PROVIDER"));
            environment.put("GENERATION_PROVIDER_MODE", "disabled");
            Process child = builder.start();
            if (!child.waitFor(10, TimeUnit.MINUTES)) {
                child.destroyForcibly();
                child.waitFor(10, TimeUnit.SECONDS);
                fail("ROUTE_PROCESS_TIMEOUT", "Route child exceeded its time limit.", 5);
            }
            if (Files.size(stdout) > 1_000_000 || Files.size(stderr) > 1_000_000)
                fail("ROUTE_PROCESS_OUTPUT_LIMIT", "Route child output exceeded its limit.", 5);
            return child.exitValue();
        } finally {
            Files.deleteIfExists(stdout);
            Files.deleteIfExists(stderr);
        }
    }

    private ObservedRun inspect(Path storeDir, Path packDir) throws Exception {
        SessionTypes.OwnerRef owner = new SessionTypes.OwnerRef("local-console");
        try (LocalSessionStore store = LocalSessionStore.open(storeDir, clock)) {
            var page = store.list(owner, new SessionTypes.RunQuery(true, true), null, 10);
            if (page.runs().size() != 1) fail("ROUTE_STORE_INVALID", "Route store must contain exactly one run.", 5);
            var run = store.find(page.runs().getFirst().runId(), owner)
                    .orElseThrow(() -> new IllegalStateException("saved run missing"));
            var commands = store.list(run.id());
            int accepted = (int) commands.stream().filter(c -> c.state() == SessionTypes.CommandState.DONE
                    && Boolean.TRUE.equals(c.accepted())).count();
            int pending = (int) commands.stream().filter(c -> c.state() == SessionTypes.CommandState.IN_PROGRESS).count();
            var snapshot = run.snapshot();
            if (snapshot == null) fail("ROUTE_STORE_INVALID", "Saved run has no snapshot.", 5);
            String outcome = snapshot.state().outcome();
            return new ObservedRun(run.status().name(), run.revision(), snapshot.step(), snapshot.state().sceneId(),
                    outcome, resultClass(packDir, outcome), snapshot.state().objectives().entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> e.getValue().name(),
                            (a, b) -> a, LinkedHashMap::new)), accepted, pending);
        }
    }

    private String resultClass(Path packDir, String outcomeId) throws Exception {
        if (outcomeId == null) return "UNKNOWN";
        JsonNode rules = JSON.readTree(Files.readString(packDir.resolve("rules.json"), StandardCharsets.UTF_8));
        for (JsonNode outcome : rules.path("outcomes"))
            if (outcomeId.equals(outcome.path("id").asString())) return outcome.path("resultClass").asString("UNKNOWN");
        return "UNKNOWN";
    }

    private Map<String, String> preset(String route) {
        return switch (route) {
            case "success" -> Map.of("story-tone", "cozy", "interests", "nature", "companion-focus", "keeper");
            case "partial" -> Map.of("story-tone", "adventurous", "interests", "puzzles", "companion-focus", "inventor");
            default -> Map.of("story-tone", "cozy", "interests", "making-things", "companion-focus", "heron");
        };
    }

    private void writeEvidence(Options options, Suite suite, Pricing pricing, ObservingGenerator observed,
                               List<RouteResult> routes, Path root, UUID id, Instant started, boolean passed) throws Exception {
        Instant ended = clock.instant();
        ObjectNode manifest = JSON.createObjectNode();
        manifest.put("schemaVersion", 1).put("certificationId", id.toString())
                .put("scope", options.scope.toUpperCase(Locale.ROOT)).put("mode", options.live ? "LIVE" : "OFFLINE")
                .put("startedAt", started.toString()).put("endedAt", ended.toString())
                .put("result", passed ? "PASS" : "FAIL").put("suiteId", suite.id)
                .put("packId", suite.packId).put("packVersion", suite.packVersion)
                .put("packDigest", digestTree(options.packDir)).put("suiteDigest", sha256(options.suite))
                .put("thresholdProfile", "engine-live-v1").put("javaVersion", System.getProperty("java.version"))
                .put("providerId", options.live ? "anthropic" : "disabled");
        if (options.live) {
            manifest.put("requestedModelId", pricing.modelId).put("pricingDigest", sha256(options.pricingFile))
                    .put("apiVersion", "2023-06-01").put("connectTimeout", "PT5S")
                    .put("responseTimeout", "PT25S");
        }
        writeJson(root.resolve("manifest.json"), manifest);
        ObjectNode summary = JSON.createObjectNode();
        summary.put("result", passed ? "PASS" : "FAIL").put("routesCompleted", routes.size())
                .put("providerAttempts", observed == null ? 0 : observed.observations.size())
                .put("estimatedCostUsd", observed == null ? "0" : observed.cost.toPlainString());
        writeJson(root.resolve("summary.json"), summary);
        List<String> observationLines = new ArrayList<>();
        if (observed != null) for (ObjectNode observation : observed.observations)
            observationLines.add(JSON.writeValueAsString(observation));
        Files.write(root.resolve("observations.ndjson"), observationLines, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("console.txt"), "certification=" + id + "\nresult=" + (passed ? "PASS" : "FAIL")
                + "\nroutes=" + routes.size() + "\n", StandardCharsets.UTF_8);
        writeChecksums(root);
    }

    private void safeFailureEvidence(Path root, UUID id, Instant started, CertificationFailure failure) {
        try {
            Files.createDirectories(root);
            ObjectNode manifest = JSON.createObjectNode().put("schemaVersion", 1).put("certificationId", id.toString())
                    .put("startedAt", started.toString()).put("endedAt", clock.instant().toString())
                    .put("result", failure.exit == 4 ? "INCONCLUSIVE" : "FAIL");
            manifest.putArray("failureCodes").add(failure.code);
            writeJson(root.resolve("manifest.json"), manifest);
            writeChecksums(root);
        } catch (Exception ignored) { }
    }

    private static void writeChecksums(Path root) throws Exception {
        List<String> lines = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile).filter(path -> !path.getFileName().toString().equals("checksums.sha256"))
                    .sorted().forEach(path -> {
                        try { lines.add(sha256(path) + "  " + root.relativize(path).toString().replace('\\', '/')); }
                        catch (Exception e) { throw new IllegalStateException(e); }
                    });
        }
        Files.write(root.resolve("checksums.sha256"), lines, StandardCharsets.UTF_8);
    }

    private static void writeJson(Path path, JsonNode value) throws Exception {
        Files.writeString(path, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value) + "\n", StandardCharsets.UTF_8);
    }

    private static String digestTree(Path root) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                digest.update(root.relativize(path).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(Files.readAllBytes(path));
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static void moveAtomically(Path source, Path target) throws Exception {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(source, target); }
    }

    private static void deleteTree(Path root) {
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (Exception ignored) { } });
        } catch (Exception ignored) { }
    }

    private static int indexOfQuit(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) if (lines.get(i).trim().equalsIgnoreCase("/quit")) return i;
        return -1;
    }

    private static int countChoices(List<String> lines) {
        return (int) lines.stream().map(String::trim).filter(line -> !line.isBlank() && !line.startsWith("/")).count();
    }

    private static void fail(String code, String message) { throw new CertificationFailure(code, message, 2); }
    private static void fail(String code, String message, int exit) { throw new CertificationFailure(code, message, exit); }

    private static String usage() {
        return "Usage: java -jar engine-cli.jar certify --mode offline --pack-dir <dir> --suite <suite.json> --evidence-dir <dir>\n"
                + "       java -jar engine-cli.jar certify --mode live [--scope canary|full] --pack-dir <dir> --suite <suite.json>\n"
                + "              --pricing-file <json> --max-provider-calls <n> --max-cost-usd <decimal>\n"
                + "              --evidence-dir <dir> --confirm-live-provider-cost\n"
                + "Live certification makes paid provider calls. API keys are read only from the environment.";
    }

    private static final class Options {
        Path packDir, suite, evidenceDir, pricingFile;
        boolean live, confirm, help, continueAfterFailure;
        String scope = "full";
        Integer maxCalls;
        BigDecimal maxCost;

        static Options parse(String[] args) {
            Options value = new Options();
            if (args == null || args.length < 2) fail("CERTIFICATION_ARGUMENT_INVALID", "Certification options are required.");
            for (int i = 1; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--help" -> value.help = true;
                    case "--confirm-live-provider-cost" -> value.confirm = true;
                    case "--continue-after-failure" -> value.continueAfterFailure = true;
                    case "--mode" -> { String mode = next(args, ++i, arg); if (!mode.equals("offline") && !mode.equals("live")) fail("CERTIFICATION_ARGUMENT_INVALID", "Mode must be offline or live."); value.live = mode.equals("live"); }
                    case "--scope" -> { value.scope = next(args, ++i, arg); if (!Set.of("full", "canary").contains(value.scope)) fail("CERTIFICATION_ARGUMENT_INVALID", "Scope must be full or canary."); }
                    case "--pack-dir" -> value.packDir = Path.of(next(args, ++i, arg)).toAbsolutePath().normalize();
                    case "--suite" -> value.suite = Path.of(next(args, ++i, arg)).toAbsolutePath().normalize();
                    case "--evidence-dir" -> value.evidenceDir = Path.of(next(args, ++i, arg)).toAbsolutePath().normalize();
                    case "--pricing-file" -> value.pricingFile = Path.of(next(args, ++i, arg)).toAbsolutePath().normalize();
                    case "--max-provider-calls" -> { try { value.maxCalls = Integer.parseInt(next(args, ++i, arg)); } catch (NumberFormatException e) { fail("CERTIFICATION_ARGUMENT_INVALID", "Call limit must be an integer."); } if (value.maxCalls < 1) fail("CERTIFICATION_ARGUMENT_INVALID", "Call limit must be positive."); }
                    case "--max-cost-usd" -> { try { value.maxCost = new BigDecimal(next(args, ++i, arg)); } catch (NumberFormatException e) { fail("CERTIFICATION_ARGUMENT_INVALID", "Cost limit must be decimal."); } if (value.maxCost.signum() <= 0 || value.maxCost.scale() > 6) fail("CERTIFICATION_ARGUMENT_INVALID", "Cost limit must be positive with at most six decimals."); }
                    default -> fail("CERTIFICATION_ARGUMENT_INVALID", "Unknown certification option: " + arg);
                }
            }
            if (!value.help && (value.packDir == null || value.suite == null || value.evidenceDir == null))
                fail("CERTIFICATION_ARGUMENT_INVALID", "Pack, suite, and evidence directories are required.");
            return value;
        }

        private static String next(String[] args, int index, String option) {
            if (index >= args.length) fail("CERTIFICATION_ARGUMENT_INVALID", "Missing value for " + option + ".");
            return args[index];
        }
    }

    private record Route(String id, Path script, String expectedResult) { }
    private record ObservedRun(String status, long revision, long step, String endingSceneId, String outcomeId,
                               String resultClass, Map<String, String> objectives, int acceptedChoices,
                               int pendingCommands) {
        RouteResult result(String routeId, boolean passed, int processes, Long checkpointRevision) {
            return new RouteResult(routeId, passed, processes, checkpointRevision, status, revision, step,
                    endingSceneId, outcomeId, resultClass, objectives, acceptedChoices, pendingCommands);
        }
    }

    private record RouteResult(String id, boolean passed, int restartProcesses, Long checkpointRevision,
                               String status, long finalRevision, long step, String endingSceneId,
                               String outcomeId, String resultClass, Map<String, String> objectives,
                               int acceptedChoices, int pendingCommands) {
        ObjectNode json() {
            ObjectNode node = JSON.createObjectNode().put("routeId", id).put("completed", passed)
                    .put("restartProcesses", restartProcesses).put("finalRevision", finalRevision)
                    .put("step", step).put("status", status).put("endingSceneId", endingSceneId)
                    .put("outcomeId", outcomeId).put("resultClass", resultClass)
                    .put("acceptedStoryChoices", acceptedChoices).put("pendingCommands", pendingCommands)
                    .put("terminalReloaded", true);
            if (checkpointRevision != null) node.put("checkpointRevision", checkpointRevision);
            ObjectNode objectiveNode = node.putObject("objectives");
            objectives.forEach(objectiveNode::put);
            return node;
        }
    }

    private static final class Suite {
        final String id, packId, packVersion, resumeRoute;
        final List<Route> routes;
        Suite(String id, String packId, String packVersion, String resumeRoute, List<Route> routes) {
            this.id = id; this.packId = packId; this.packVersion = packVersion; this.resumeRoute = resumeRoute; this.routes = routes;
        }
        static Suite read(Path file, Path packDir) throws Exception {
            JsonNode root = JSON.readTree(Files.readString(file, StandardCharsets.UTF_8));
            requireObject(root, ROOT_FIELDS, "suite");
            if (root.path("schemaVersion").asInt(-1) != 1 || root.path("prefetch").asBoolean(true)
                    || !"FORBIDDEN".equals(root.path("freeText").asString())) fail("SUITE_INVALID", "Unsupported suite policy.");
            JsonNode pack = root.get("pack");
            requireObject(pack, Set.of("id", "version"), "pack");
            if (!packDir.getFileName().toString().equals(pack.path("id").asString())) fail("SUITE_PACK_MISMATCH", "Suite pack does not match pack directory.");
            JsonNode manifest = JSON.readTree(Files.readString(packDir.resolve("pack.json"), StandardCharsets.UTF_8));
            if (!pack.path("id").asString().equals(manifest.path("id").asString())
                    || !pack.path("version").asString().equals(manifest.path("version").asString()))
                fail("SUITE_PACK_MISMATCH", "Suite pack identity or version does not match pack.json.");
            Path suiteRoot = file.getParent().toRealPath();
            List<Route> routes = new ArrayList<>();
            for (JsonNode route : root.path("routes")) {
                requireObject(route, Set.of("id", "script", "expectedResult"), "route");
                String id = safeId(route.path("id").asString(), "route id");
                Path script = suiteRoot.resolve(route.path("script").asString()).normalize();
                if (!script.startsWith(suiteRoot) || !Files.isRegularFile(script) || Files.size(script) > 64_000)
                    fail("SUITE_PATH_INVALID", "Route script is invalid.");
                script = script.toRealPath();
                if (!script.startsWith(suiteRoot)) fail("SUITE_PATH_INVALID", "Route script escapes the suite.");
                validateScript(script);
                routes.add(new Route(id, script, safeId(route.path("expectedResult").asString(), "result")));
            }
            if (routes.size() != 3 || routes.stream().map(Route::id).distinct().count() != 3) fail("SUITE_INVALID", "Full suite requires three unique routes.");
            String resume = safeId(root.path("resumeCheckpoint").path("route").asString(), "resume route");
            return new Suite(safeId(root.path("suiteId").asString(), "suite id"), pack.path("id").asString(),
                    pack.path("version").asString(), resume, List.copyOf(routes));
        }

        private static void validateScript(Path script) throws Exception {
            Set<String> utilities = Set.of("/status", "/objectives", "/characters", "/save", "/quit");
            int choices = 0;
            for (String raw : Files.readAllLines(script, StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (line.isBlank()) continue;
                if (line.startsWith("/")) {
                    if (!utilities.contains(line.toLowerCase(Locale.ROOT))) fail("SUITE_SCRIPT_INVALID", "Route uses a forbidden utility command.");
                } else {
                    safeId(line, "choice id");
                    choices++;
                }
            }
            if (choices != 8) fail("SUITE_SCRIPT_INVALID", "Every full route must contain exactly eight story choices.");
        }
    }

    private static final class Pricing {
        final String modelId;
        final BigDecimal input, output, cacheRead, cacheWrite, reserve;
        Pricing(String modelId, BigDecimal input, BigDecimal output, BigDecimal cacheRead, BigDecimal cacheWrite, BigDecimal reserve) {
            this.modelId = modelId; this.input = input; this.output = output; this.cacheRead = cacheRead; this.cacheWrite = cacheWrite; this.reserve = reserve;
        }
        static Pricing read(Path file, Instant now) throws Exception {
            JsonNode root = JSON.readTree(Files.readString(file, StandardCharsets.UTF_8));
            requireObject(root, PRICE_FIELDS, "pricing");
            if (root.path("schemaVersion").asInt(-1) != 1 || !"anthropic".equals(root.path("providerId").asString())
                    || !"USD".equals(root.path("currency").asString())) fail("PRICING_INVALID", "Unsupported pricing tuple.");
            Instant reviewed;
            try { reviewed = Instant.parse(root.path("reviewedAt").asString()); Instant.parse(root.path("effectiveAt").asString()); }
            catch (RuntimeException e) { fail("PRICING_INVALID", "Pricing timestamps are invalid."); return null; }
            if (reviewed.isAfter(now.plus(5, ChronoUnit.MINUTES)) || reviewed.isBefore(now.minus(7, ChronoUnit.DAYS)))
                fail("PRICING_STALE", "Pricing review must be no older than seven days.");
            BigDecimal input = decimal(root, "inputUsdPerMillionTokens"), output = decimal(root, "outputUsdPerMillionTokens");
            BigDecimal read = decimal(root, "cacheReadUsdPerMillionTokens"), write = decimal(root, "cacheWriteUsdPerMillionTokens");
            BigDecimal reserve = decimal(root, "reservedMaximumUsdPerCall");
            boolean free = root.path("freeTier").asBoolean(false);
            if (!free && input.signum() == 0 && output.signum() == 0 && read.signum() == 0 && write.signum() == 0)
                fail("PRICING_INVALID", "Zero pricing requires freeTier=true.");
            if (reserve.signum() <= 0) fail("PRICING_INVALID", "Reserved maximum per call must be positive.");
            return new Pricing(safeId(root.path("modelId").asString(), "model id"), input, output, read, write, reserve);
        }
        BigDecimal cost(TextGenerationResult.TokenUsage usage) {
            return input.multiply(BigDecimal.valueOf(usage.inputTokens()))
                    .add(output.multiply(BigDecimal.valueOf(usage.outputTokens())))
                    .add(cacheRead.multiply(BigDecimal.valueOf(usage.cacheReadTokens())))
                    .add(cacheWrite.multiply(BigDecimal.valueOf(usage.cacheWriteTokens())))
                    .divide(BigDecimal.valueOf(1_000_000), 12, RoundingMode.UNNECESSARY).stripTrailingZeros();
        }
    }

    private static final class ObservingGenerator implements TextGenerator {
        final TextGenerator delegate;
        final String expectedModel;
        final Pricing pricing;
        final int callLimit;
        final BigDecimal costLimit;
        final List<ObjectNode> observations = new ArrayList<>();
        BigDecimal cost = BigDecimal.ZERO;
        String failureCode;
        ObservingGenerator(TextGenerator delegate, String expectedModel, Pricing pricing, int callLimit, BigDecimal costLimit) {
            this.delegate = delegate; this.expectedModel = expectedModel; this.pricing = pricing; this.callLimit = callLimit; this.costLimit = costLimit;
        }
        @Override public synchronized TextGenerationResult generate(StructuredGenerationRequest request) {
            if (observations.size() + 1 > callLimit) throw gate("CALL_LIMIT_REACHED");
            if (cost.add(pricing.reserve).compareTo(costLimit) > 0) throw gate("COST_LIMIT_REACHED");
            long start = System.nanoTime();
            try {
                TextGenerationResult result = delegate.generate(request);
                long elapsed = Duration.ofNanos(System.nanoTime() - start).toMillis();
                if (!expectedModel.equals(result.modelId())) throw gate("MODEL_ID_MISMATCH");
                if (result.usage() == null) throw gate("USAGE_MISSING");
                cost = cost.add(pricing.cost(result.usage()));
                ObjectNode observation = JSON.createObjectNode().put("sequence", observations.size() + 1)
                        .put("stage", safeObservationId(request.taskId())).put("attemptKind", "PRIMARY")
                        .put("elapsedMillis", elapsed).put("outcomeCode", result.finishReason().name())
                        .put("modelId", result.modelId()).put("inputTokens", result.usage().inputTokens())
                        .put("outputTokens", result.usage().outputTokens()).put("cacheReadTokens", result.usage().cacheReadTokens())
                        .put("cacheWriteTokens", result.usage().cacheWriteTokens());
                observations.add(observation);
                if (elapsed > 27_000) throw gate("LATENCY_GATE_FAILED");
                return result;
            } catch (RuntimeException failure) {
                if (failureCode == null) failureCode = "PROVIDER_ATTEMPT_FAILED";
                throw failure;
            }
        }
        private IllegalStateException gate(String code) { failureCode = code; return new IllegalStateException(code); }
    }

    private static void requireObject(JsonNode node, Set<String> allowed, String label) {
        if (node == null || !node.isObject()) fail("SUITE_INVALID", label + " must be an object.");
        for (String field : node.propertyNames())
            if (!allowed.contains(field)) fail("SUITE_INVALID", "Unknown " + label + " property.");
    }

    private static BigDecimal decimal(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isString()) fail("PRICING_INVALID", field + " must be a decimal string.");
        try {
            BigDecimal decimal = new BigDecimal(value.asString());
            if (decimal.signum() < 0 || decimal.scale() > 6 || decimal.precision() > 18) throw new NumberFormatException();
            return decimal;
        } catch (NumberFormatException e) { fail("PRICING_INVALID", field + " is invalid."); return null; }
    }

    private static String safeId(String value, String label) {
        if (value == null || !value.matches("[A-Za-z0-9._:-]{1,100}")) fail("SUITE_INVALID", label + " is invalid.");
        return value;
    }

    private static String safeObservationId(String value) {
        if (value == null) return "unknown";
        String safe = value.replaceAll("[^A-Za-z0-9._:-]", "_");
        return safe.substring(0, Math.min(100, safe.length()));
    }

    private static final class CertificationFailure extends RuntimeException {
        final String code; final int exit;
        CertificationFailure(String code, String message, int exit) { super(message); this.code = code; this.exit = exit; }
    }
}
