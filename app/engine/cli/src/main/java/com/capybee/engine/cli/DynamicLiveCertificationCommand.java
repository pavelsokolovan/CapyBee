package com.capybee.engine.cli;

import com.capybee.engine.adapter.Randoms;
import com.capybee.engine.adapter.UuidIdSource;
import com.capybee.engine.adapter.anthropic.AnthropicConfig;
import com.capybee.engine.adapter.anthropic.AnthropicTextGenerator;
import com.capybee.engine.port.StructuredGenerationRequest;
import com.capybee.engine.port.TextGenerationResult;
import com.capybee.engine.port.TextGenerator;
import com.capybee.engine.session.SessionTypes;
import com.capybee.engine.session.adapter.LocalSessionStore;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Paid, opt-in certification of the production dynamic start and resume boundary. */
final class DynamicLiveCertificationCommand {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> SUITE_FIELDS = Set.of("schemaVersion", "suiteId", "thresholdProfileId", "cases");
    private static final Set<String> CASE_FIELDS = Set.of("id", "scope", "seed", "locale", "length", "challenge", "checkpointCommands", "maxCommands");
    private final PrintStream out;
    private final PrintStream err;
    private final Clock clock;

    DynamicLiveCertificationCommand(PrintStream out, PrintStream err, Clock clock) {
        this.out = out; this.err = err; this.clock = clock;
    }

    int run(String[] arguments) {
        Options options;
        try { options = Options.parse(arguments); }
        catch (Failure failure) { err.println(failure.code); return 2; }
        UUID id = UUID.randomUUID();
        Path incomplete = options.evidence.resolve(id + ".incomplete");
        Instant started = clock.instant();
        try {
            preflight(options, incomplete);
            Suite suite = Suite.read(options.suite, options.scope);
            Pricing pricing = Pricing.read(options.pricing, clock.instant());
            AnthropicConfig config = AnthropicConfig.fromEnvironment();
            if (!config.modelId().equals(pricing.modelId)) fail("PRICING_MODEL_MISMATCH", 2);
            MeteredGenerator generator = new MeteredGenerator(new AnthropicTextGenerator(config), pricing,
                    options.maxCalls, options.maxCost, config.modelId());
            Files.createDirectories(incomplete.resolve("runs"));
            List<RunEvidence> runs = new ArrayList<>();
            for (Case testCase : suite.cases) {
                RunEvidence run = execute(options, testCase, generator);
                runs.add(run);
                write(incomplete.resolve("runs").resolve(testCase.id + ".json"), run.json());
            }
            boolean full = "full".equals(options.scope);
            boolean passed = runs.stream().allMatch(RunEvidence::passed) && (!full || runs.size() >= 2)
                    && (!full || runs.stream().anyMatch(r -> r.restartProcesses == 2));
            writeEvidence(options, suite, pricing, generator, runs, incomplete, id, started, passed);
            Path finalized = options.evidence.resolve(id.toString());
            Files.move(incomplete, finalized, StandardCopyOption.ATOMIC_MOVE);
            out.println("certify source=dynamic mode=live scope=" + options.scope + " result=" + (passed ? "PASS" : "FAIL")
                    + " status=" + (passed && full ? "live-certified" : "diagnostic") + " evidence-id=" + id);
            return passed ? 0 : 5;
        } catch (Failure failure) {
            err.println(failure.code);
            failureEvidence(incomplete, id, started, failure.code);
            return failure.exit;
        } catch (Exception failure) {
            err.println("DYNAMIC_CERTIFICATION_LOCAL_FAILURE");
            failureEvidence(incomplete, id, started, "DYNAMIC_CERTIFICATION_LOCAL_FAILURE");
            return 3;
        }
    }

    private void preflight(Options o, Path incomplete) {
        if (PlayMain.ProviderMode.fromEnvironment() != PlayMain.ProviderMode.CERTIFICATION) fail("PROVIDER_MODE_INVALID", 2);
        if (Boolean.parseBoolean(System.getenv("ANTHROPIC_DEBUG"))) fail("DEBUG_LOGGING_FORBIDDEN", 2);
        if (!o.confirm) fail("LIVE_CONFIRMATION_REQUIRED", 2);
        try { if (!Files.isDirectory(o.profile) || !ProfileDirectory.load(o.profile).valid()) fail("PROFILE_INVALID", 2); }
        catch (java.io.IOException invalid) { fail("PROFILE_INVALID", 2); }
        if (!Files.isRegularFile(o.suite)) fail("SUITE_INVALID", 2);
        if (!Files.isRegularFile(o.pricing)) fail("PRICING_INVALID", 2);
        if (Files.exists(incomplete)) fail("EVIDENCE_EXISTS", 2);
        try { Files.createDirectories(o.evidence); }
        catch (Exception e) { fail("EVIDENCE_UNWRITABLE", 3); }
    }

    private RunEvidence execute(Options options, Case testCase, MeteredGenerator generator) throws Exception {
        Path store = Files.createTempDirectory("capybee-live-dynamic-");
        try {
            int beforeCalls = generator.calls;
            Path script = Files.createTempFile("capybee-live-start-", ".script");
            StringBuilder commands = new StringBuilder("\ny\n1\n1\n1\n");
            int firstCommands = "full".equals(testCase.scope) ? testCase.checkpointCommands : 1;
            commands.append("1\n".repeat(Math.max(1, firstCommands))).append("/save\n/quit\n");
            Files.writeString(script, commands, StandardCharsets.UTF_8);
            int first = console(generator, options.profile, store, script, testCase.seed, false);
            Snapshot checkpoint = inspect(store);
            int processes = 1;
            Snapshot finished = checkpoint;
            if ("full".equals(testCase.scope) && first == 0 && !checkpoint.ended) {
                Path resume = Files.createTempFile("capybee-live-resume-", ".script");
                Files.writeString(resume, "1\n".repeat(testCase.maxCommands), StandardCharsets.UTF_8);
                int second = child(options.profile, store, resume);
                processes = 2;
                finished = inspect(store);
                if (second != 0) return RunEvidence.failed(testCase.id, processes, "RESUME_PROCESS_FAILED");
            }
            boolean pass = first == 0 && ("canary".equals(testCase.scope) || finished.ended)
                    && checkpoint.runId.equals(finished.runId)
                    && checkpoint.gameFingerprint.equals(finished.gameFingerprint)
                    && checkpoint.definitionFingerprint.equals(finished.definitionFingerprint)
                    && generator.calls == beforeCalls + checkpoint.providerCalls;
            return new RunEvidence(testCase.id, pass, processes, finished.runId.toString(), testCase.seed,
                    checkpoint.revision, finished.revision, checkpoint.gameFingerprint, checkpoint.definitionFingerprint,
                    checkpoint.providerCalls, finished.providerCalls, finished.resultClass, pass ? null : "RUN_INVALID");
        } finally { deleteTree(store); }
    }

    private int console(TextGenerator generator, Path profile, Path store, Path script, long seed, boolean resume) {
        List<String> args = new ArrayList<>(List.of("play", "--dynamic", "--profile-dir", profile.toString(),
                "--store-dir", store.toString(), "--script", script.toString(), "--no-color"));
        if (resume) args.add("--continue"); else { args.add("--new"); args.add("--seed"); args.add(Long.toString(seed)); }
        return new ConsoleApp(new ByteArrayInputStream(new byte[0]), new PrintStream(ByteArrayOutputStream.nullOutputStream()),
                new PrintStream(ByteArrayOutputStream.nullOutputStream()), clock, new UuidIdSource(), Randoms.seeded(seed), generator)
                .run(args.toArray(String[]::new));
    }

    private int child(Path profile, Path store, Path script) throws Exception {
        Path java = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java");
        List<String> command = new ArrayList<>(List.of(java.toString(), "-Dfile.encoding=UTF-8", "-cp", System.getProperty("java.class.path"),
                PlayMain.class.getName(), "play", "--dynamic", "--profile-dir", profile.toString(), "--store-dir", store.toString(),
                "--script", script.toString(), "--continue", "--offline", "--no-color"));
        ProcessBuilder builder = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().keySet().removeIf(k -> k.toUpperCase(Locale.ROOT).contains("ANTHROPIC") || k.toUpperCase(Locale.ROOT).startsWith("GENERATION_PROVIDER"));
        builder.environment().put("GENERATION_PROVIDER_MODE", "disabled");
        Process process = builder.start();
        if (!process.waitFor(2, TimeUnit.MINUTES)) { process.destroyForcibly(); return 3; }
        return process.exitValue();
    }

    private Snapshot inspect(Path store) {
        try (LocalSessionStore opened = LocalSessionStore.open(store, clock)) {
            var page = opened.list(new SessionTypes.OwnerRef("local-console"), SessionTypes.RunQuery.visible(), null, 10);
            var run = opened.find(page.runs().getFirst().runId(), new SessionTypes.OwnerRef("local-console")).orElseThrow();
            var dynamic = run.snapshot().dynamic();
            String result = run.snapshot().state().outcome() == null ? "IN_PROGRESS" : dynamic.bundle().definition().runtimeRules().outcomes().stream()
                    .filter(o -> o.id().equals(run.snapshot().state().outcome())).findFirst().orElseThrow().resultClass().name();
            return new Snapshot(run.id(), run.revision(), run.snapshot().state().ended(), dynamic.bundle().gameFingerprint(),
                    dynamic.bundle().definitionFingerprint(), dynamic.totalProviderCalls(), result);
        }
    }

    private void writeEvidence(Options o, Suite suite, Pricing pricing, MeteredGenerator meter, List<RunEvidence> runs,
                               Path root, UUID id, Instant started, boolean passed) throws Exception {
        ObjectNode manifest = JSON.createObjectNode();
        manifest.put("schemaVersion", 1).put("certificationId", id.toString()).put("mode", "live").put("scope", o.scope)
                .put("result", passed ? "PASS" : "FAIL").put("status", passed && "full".equals(o.scope) ? "live-certified" : "diagnostic")
                .put("startedAt", started.toString()).put("completedAt", clock.instant().toString())
                .put("expiresAt", clock.instant().plus(14, ChronoUnit.DAYS).toString()).put("suiteDigest", sha256(o.suite))
                .put("profileFingerprint", ProfileDirectory.load(o.profile).profile().orElseThrow().identity().fingerprint()).put("providerId", pricing.providerId)
                .put("modelId", meter.returnedModel == null ? meter.expectedModel : meter.returnedModel).put("pricingDigest", sha256(o.pricing))
                .put("calls", meter.calls).put("inputTokens", meter.input).put("outputTokens", meter.output)
                .put("cacheReadTokens", meter.cacheRead).put("cacheWriteTokens", meter.cacheWrite)
                .put("estimatedCostUsd", meter.cost.toPlainString()).put("maxProviderCalls", o.maxCalls)
                .put("maxCostUsd", o.maxCost.toPlainString()).put("restartProcessCount", runs.stream().mapToInt(r -> r.restartProcesses).max().orElse(0));
        write(root.resolve("manifest.json"), manifest);
        write(root.resolve("summary.json"), JSON.createObjectNode().put("schemaVersion", 1).put("suiteId", suite.id)
                .put("runCount", runs.size()).put("passed", passed));
        List<String> checksums = new ArrayList<>();
        try (var paths = Files.walk(root)) { for (Path p : paths.filter(Files::isRegularFile).sorted().toList())
            checksums.add(sha256(p) + "  " + root.relativize(p).toString().replace('\\', '/')); }
        Files.write(root.resolve("checksums.sha256"), checksums, StandardCharsets.UTF_8);
    }

    private void failureEvidence(Path root, UUID id, Instant started, String code) {
        try { Files.createDirectories(root); write(root.resolve("failure.json"), JSON.createObjectNode().put("schemaVersion", 1)
                .put("certificationId", id.toString()).put("startedAt", started.toString()).put("result", "INCOMPLETE").put("code", code)); }
        catch (Exception ignored) { }
    }
    private static void write(Path p, JsonNode n) throws Exception { Files.writeString(p, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(n), StandardCharsets.UTF_8); }
    private static String sha256(Path p) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p))); }
    private static void deleteTree(Path root) throws Exception { if (!Files.exists(root)) return; try (var p=Files.walk(root)){for(Path x:p.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(x);} }
    private static void fail(String code, int exit) { throw new Failure(code, exit); }

    private record Snapshot(UUID runId, long revision, boolean ended, String gameFingerprint, String definitionFingerprint, int providerCalls, String resultClass) { }
    private record RunEvidence(String caseId, boolean passed, int restartProcesses, String runId, long seed, long checkpointRevision,
                               long finalRevision, String gameFingerprint, String definitionFingerprint, int callsAtCheckpoint,
                               int callsAtCompletion, String resultClass, String failureCode) {
        static RunEvidence failed(String id, int processes, String code) { return new RunEvidence(id,false,processes,"",0,0,0,"","",0,0,"IN_PROGRESS",code); }
        ObjectNode json() { return JSON.createObjectNode().put("schemaVersion",1).put("caseId",caseId).put("passed",passed)
                .put("restartProcesses",restartProcesses).put("runId",runId).put("seed",seed).put("checkpointRevision",checkpointRevision)
                .put("finalRevision",finalRevision).put("gameFingerprint",gameFingerprint).put("definitionFingerprint",definitionFingerprint)
                .put("providerCallsAtCheckpoint",callsAtCheckpoint).put("providerCallsAtCompletion",callsAtCompletion)
                .put("resultClass",resultClass).put("failureCode",failureCode); }
    }

    private record Case(String id, String scope, long seed, int checkpointCommands, int maxCommands) { }
    private record Suite(String id, List<Case> cases) {
        static Suite read(Path path, String requestedScope) {
            try {
                JsonNode root=JSON.readTree(Files.readString(path)); strict(root,SUITE_FIELDS);
                if(root.path("schemaVersion").asInt()!=1||root.path("suiteId").asString().isBlank()||!root.path("cases").isArray()) fail("SUITE_INVALID",2);
                List<Case> cases=new ArrayList<>(); for(JsonNode c:root.path("cases")){strict(c,CASE_FIELDS); String scope=c.path("scope").asString();
                    if(scope.equals(requestedScope)) cases.add(new Case(c.path("id").asString(),scope,c.path("seed").asLong(),c.path("checkpointCommands").asInt(),c.path("maxCommands").asInt()));}
                if(cases.isEmpty()||("full".equals(requestedScope)&&cases.size()<2)) fail("SUITE_INVALID",2); return new Suite(root.path("suiteId").asString(),List.copyOf(cases));
            }catch(Failure f){throw f;}catch(Exception e){throw new Failure("SUITE_INVALID",2);}
        }
    }
    private record Pricing(String providerId,String modelId,BigDecimal input,BigDecimal output,BigDecimal cacheRead,BigDecimal cacheWrite,BigDecimal reserve) {
        static Pricing read(Path p,Instant now){try{JsonNode n=JSON.readTree(Files.readString(p)); Instant reviewed=Instant.parse(n.path("reviewedAt").asString());
            if(reviewed.isBefore(now.minus(30,ChronoUnit.DAYS))||!"USD".equals(n.path("currency").asString()))fail("PRICING_STALE",2);
            return new Pricing(n.path("providerId").asString(),n.path("modelId").asString(),decimal(n,"inputUsdPerMillionTokens"),decimal(n,"outputUsdPerMillionTokens"),decimal(n,"cacheReadUsdPerMillionTokens"),decimal(n,"cacheWriteUsdPerMillionTokens"),decimal(n,"reservedMaximumUsdPerCall"));
        }catch(Failure f){throw f;}catch(Exception e){throw new Failure("PRICING_INVALID",2);}}
        BigDecimal cost(TextGenerationResult.TokenUsage u){return input.multiply(BigDecimal.valueOf(u.inputTokens())).add(output.multiply(BigDecimal.valueOf(u.outputTokens()))).add(cacheRead.multiply(BigDecimal.valueOf(u.cacheReadTokens()))).add(cacheWrite.multiply(BigDecimal.valueOf(u.cacheWriteTokens()))).divide(BigDecimal.valueOf(1_000_000),6,RoundingMode.UP);}
        private static BigDecimal decimal(JsonNode n,String key){BigDecimal v=new BigDecimal(n.path(key).asString());if(v.signum()<0)fail("PRICING_INVALID",2);return v;}
    }
    private static final class MeteredGenerator implements TextGenerator {
        final TextGenerator delegate; final Pricing pricing; final int limit; final BigDecimal costLimit; final String expectedModel;
        int calls; long input,output,cacheRead,cacheWrite; BigDecimal cost=BigDecimal.ZERO; String returnedModel;
        MeteredGenerator(TextGenerator d,Pricing p,int l,BigDecimal c,String m){delegate=d;pricing=p;limit=l;costLimit=c;expectedModel=m;}
        public synchronized TextGenerationResult generate(StructuredGenerationRequest request){if(calls>=limit)fail("CALL_LIMIT_REACHED",5);if(cost.add(pricing.reserve).compareTo(costLimit)>0)fail("COST_LIMIT_REACHED",5);calls++;
            TextGenerationResult r=delegate.generate(request);if(r.usage()==null)fail("PROVIDER_USAGE_MISSING",5);if(!expectedModel.equals(r.modelId()))fail("PROVIDER_MODEL_MISMATCH",5);returnedModel=r.modelId();var u=r.usage();input+=u.inputTokens();output+=u.outputTokens();cacheRead+=u.cacheReadTokens();cacheWrite+=u.cacheWriteTokens();cost=cost.add(pricing.cost(u));return r;}
    }
    private record Options(String scope,Path profile,Path suite,Path pricing,Path evidence,int maxCalls,BigDecimal maxCost,boolean confirm){
        static Options parse(String[] a){String scope=null;Path profile=null,suite=null,pricing=null,evidence=null;Integer calls=null;BigDecimal cost=null;boolean confirm=false;
            for(int i=1;i<a.length;i++)switch(a[i]){case"--dynamic"->{}case"--mode"->{if(!"live".equals(next(a,++i)))fail("CERTIFICATION_ARGUMENT_INVALID",2);}case"--scope"->scope=next(a,++i);case"--profile-dir"->profile=Path.of(next(a,++i)).toAbsolutePath().normalize();case"--suite"->suite=Path.of(next(a,++i)).toAbsolutePath().normalize();case"--pricing-file"->pricing=Path.of(next(a,++i)).toAbsolutePath().normalize();case"--evidence-dir"->evidence=Path.of(next(a,++i)).toAbsolutePath().normalize();case"--max-provider-calls"->{try{calls=Integer.valueOf(next(a,++i));}catch(Exception e){fail("CERTIFICATION_ARGUMENT_INVALID",2);}}case"--max-cost-usd"->{try{cost=new BigDecimal(next(a,++i));}catch(Exception e){fail("CERTIFICATION_ARGUMENT_INVALID",2);}}case"--confirm-live-provider-cost"->confirm=true;default->fail("CERTIFICATION_ARGUMENT_INVALID",2);}
            if(scope==null||!(scope.equals("canary")||scope.equals("full"))||profile==null||suite==null||pricing==null||evidence==null||calls==null||calls<1||cost==null||cost.signum()<=0||cost.scale()>6)fail("CERTIFICATION_ARGUMENT_INVALID",2);return new Options(scope,profile,suite,pricing,evidence,calls,cost,confirm);}
        private static String next(String[] a,int i){if(i>=a.length)fail("CERTIFICATION_ARGUMENT_INVALID",2);return a[i];}
    }
    private static void strict(JsonNode n,Set<String> allowed){if(!n.isObject())fail("SUITE_INVALID",2);for(String k:n.propertyNames())if(!allowed.contains(k))fail("SUITE_INVALID",2);}
    private static final class Failure extends RuntimeException{final String code;final int exit;Failure(String c,int e){code=c;exit=e;}}
}
