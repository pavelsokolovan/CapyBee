package com.capybee.engine.cli;

import com.capybee.engine.adapter.anthropic.AnthropicTextGenerator;
import com.capybee.engine.adapter.Randoms;
import com.capybee.engine.adapter.StructuredInputInterpreter;
import com.capybee.engine.adapter.UuidIdSource;
import com.capybee.engine.api.Command;
import com.capybee.engine.api.Engine;
import com.capybee.engine.api.Issue;
import com.capybee.engine.api.ResumeResult;
import com.capybee.engine.api.Run;
import com.capybee.engine.api.RunRequest;
import com.capybee.engine.api.RunSnapshot;
import com.capybee.engine.api.StartResult;
import com.capybee.engine.api.StepResult;
import com.capybee.engine.api.View;
import com.capybee.engine.api.json.SnapshotCodec;
import com.capybee.engine.pack.DirectoryPackSource;
import com.capybee.engine.pack.LoadResult;
import com.capybee.engine.pack.PackId;
import com.capybee.engine.pack.json.PackLoader;
import com.capybee.engine.pack.PackRegistry;
import com.capybee.engine.port.IdSource;
import com.capybee.engine.port.TextGenerator;
import com.capybee.engine.generation.GenerationArtifact;
import com.capybee.engine.generation.GenerationCatalog;
import com.capybee.engine.generation.GenerationExecutionSummary;
import com.capybee.engine.session.MeteredTextGenerator;
import com.capybee.engine.session.RunSessionService;
import com.capybee.engine.session.SessionLimits;
import com.capybee.engine.session.SessionPorts;
import com.capybee.engine.session.SessionException;
import com.capybee.engine.session.StoreException;
import com.capybee.engine.session.SessionTypes;
import com.capybee.engine.session.adapter.InMemorySessionStore;
import com.capybee.engine.session.adapter.LocalSessionStore;

import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.random.RandomGenerator;

public final class ConsoleApp {
    private final InputStream in;
    private final PrintStream out;
    private final PrintStream err;
    private final Clock clock;
    private final IdSource ids;
    private final RandomGenerator random;
    private final TextGenerator textGenerator;
    private int printedGenerationStages;

    public ConsoleApp(InputStream in, PrintStream out, PrintStream err, Clock clock, IdSource ids, RandomGenerator random) {
        this(in, out, err, clock, ids, random, null);
    }

    public ConsoleApp(InputStream in, PrintStream out, PrintStream err, Clock clock, IdSource ids,
                      RandomGenerator random, TextGenerator textGenerator) {
        this.in = in;
        this.out = out;
        this.err = err;
        this.clock = clock;
        this.ids = ids;
        this.random = random;
        this.textGenerator = textGenerator;
    }

    public int run(String[] args) {
        ConsoleArgs.ParseResult parsed = ConsoleArgs.parse(args);
        if (!parsed.ok()) {
            err.println(parsed.error());
            return 2;
        }

        ConsoleOptions options = parsed.options();
        printedGenerationStages = 0;
        if (options.help()) {
            out.println(consoleUsage());
            return 0;
        }

        if (options.packDir() == null) {
            err.println("A pack directory is required.");
            return 2;
        }
        if (options.liveSmoke() && !(textGenerator instanceof AnthropicTextGenerator)) {
            err.println("LIVE_PROVIDER_NOT_CONFIGURED: --live-smoke requires the configured real provider.");
            return 4;
        }

        Path packDir = options.packDir().toAbsolutePath().normalize();
        String packName = packDir.getFileName() == null ? "" : packDir.getFileName().toString();
        PackId packId = new PackId(packName);
        LoadResult load;
        try {
            load = new PackLoader(Engine.sectionValidators()).tryLoad(packId, new DirectoryPackSource(packDir));
        } catch (RuntimeException exception) {
            err.println("PACK_SOURCE_INVALID: " + exception.getMessage());
            return 1;
        }
        if (!load.ok()) {
            for (var issue : load.issues()) {
                err.println("ERROR|WARNING " + issue.code() + " " + issue.message());
            }
            return 1;
        }

        var pack = load.pack().orElseThrow();
        if (options.session()) return runSessionMode(options, packId, pack);

        var engineBuilder = Engine.builder(new PackRegistry(List.of(pack)))
                .clock(clock)
                .random(random)
            .ids(ids);
        if (textGenerator != null) engineBuilder.textGenerator(textGenerator);
        var engine = engineBuilder.build();

        Run run;
        if (options.load() != null) {
            try {
                String json = Files.readString(options.load(), StandardCharsets.UTF_8);
                var snapshotParse = SnapshotCodec.fromJson(json);
                if (snapshotParse.snapshot() == null || !snapshotParse.issues().isEmpty() && snapshotParse.issues().stream().anyMatch(issue -> issue.severity() == Issue.Severity.ERROR)) {
                    for (var issue : snapshotParse.issues()) {
                        err.println(issue.code() + ": " + issue.message());
                    }
                    return 3;
                }
                ResumeResult resumeResult = engine.resume(snapshotParse.snapshot());
                if (!resumeResult.ok()) {
                    for (var issue : resumeResult.issues()) {
                        err.println(issue.code() + ": " + issue.message());
                    }
                    return 3;
                }
                run = resumeResult.run();
            } catch (Exception exception) {
                err.println("SNAPSHOT_INVALID: " + exception.getMessage());
                return 3;
            }
        } else {
                StartResult startResult = engine.start(new RunRequest(packId, options.locale(), options.inputs(),
                    options.seed(), options.generated()));
            if (!startResult.ok()) {
                for (var issue : startResult.issues()) {
                    err.println(issue.code() + ": " + issue.message());
                }
                return 3;
            }
            run = startResult.run();
        }

        boolean scripted = options.script() != null;
        BufferedReader reader;
        try {
            reader = scripted
                    ? Files.newBufferedReader(options.script(), StandardCharsets.UTF_8)
                    : new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception exception) {
            err.println("SCRIPT_INVALID: " + exception.getMessage());
            return 2;
        }

        out.println("Running " + packId.value() + " in locale " + run.snapshot().locale().value());
        View view = run.view();
        out.println(ConsoleRenderer.render(view));
        printGenerationSummary(run);

        if (view.ended()) {
            return smokeResult(options, run);
        }

        inputLoop:
        while (true) {
            if (!scripted) out.print("> ");
            String line = readLine(reader);
            if (line == null) {
                if (scripted) return options.liveSmoke() ? smokeIncomplete() : 0;
                out.println();
                out.println(ConsoleChrome.EOF);
                return 0;
            }
            if (scripted) out.println("> " + line);
            String trimmed = line.trim();
            if (trimmed.equalsIgnoreCase("q") || trimmed.equalsIgnoreCase("quit")) {
                StepResult stepResult = run.apply(new Command.Abandon());
                if (stepResult.accepted()) {
                    out.println(ConsoleRenderer.render(stepResult.view()));
                    printGenerationSummary(run);
                }
                return options.liveSmoke() ? smokeIncomplete() : 0;
            }
            if (trimmed.isEmpty()) {
                StepResult stepResult = run.apply(new Command.Advance());
                if (stepResult.accepted()) {
                    out.println(ConsoleRenderer.render(stepResult.view()));
                    printGenerationSummary(run);
                    view = run.view();
                } else {
                    out.println(stepResult.issues().getFirst().message());
                }
                if (run.view().ended()) {
                    return smokeResult(options, run);
                }
                continue;
            }
            if (trimmed.equals("?")) {
                out.println(ConsoleChrome.HELP);
                continue;
            }
            String moveTarget = moveTarget(trimmed);
            if (moveTarget != null) {
                StepResult stepResult = run.apply(new Command.Move(moveTarget));
                if (stepResult.accepted()) {
                    out.println(ConsoleRenderer.render(stepResult.view()));
                    printGenerationSummary(run);
                } else {
                    out.println(stepResult.issues().getFirst().message());
                }
                if (run.view().ended()) return smokeResult(options, run);
                view = run.view();
                continue;
            }
            if (view.options() != null && !view.options().isEmpty()) {
                try {
                    int index = Integer.parseInt(trimmed) - 1;
                    if (index >= 0 && index < view.options().size()) {
                        StepResult stepResult = run.apply(new Command.Choose(view.options().get(index).id()));
                        if (stepResult.accepted()) {
                            out.println(ConsoleRenderer.render(stepResult.view()));
                            printGenerationSummary(run);
                        } else {
                            out.println(stepResult.issues().getFirst().message());
                        }
                        if (run.view().ended()) {
                            return smokeResult(options, run);
                        }
                        view = run.view();
                        continue inputLoop;
                    }
                } catch (NumberFormatException ignored) {
                    // fall through to default handling
                }
                for (var option : view.options()) {
                    if (option.id().equalsIgnoreCase(trimmed)) {
                        StepResult stepResult = run.apply(new Command.Choose(option.id()));
                        if (stepResult.accepted()) {
                            out.println(ConsoleRenderer.render(stepResult.view()));
                            printGenerationSummary(run);
                        } else {
                            out.println(stepResult.issues().getFirst().message());
                        }
                        if (run.view().ended()) {
                            return smokeResult(options, run);
                        }
                        view = run.view();
                        continue inputLoop;
                    }
                }
                StepResult textResult = run.apply(new Command.SubmitText(line));
                if (textResult.accepted()) {
                    out.println(ConsoleRenderer.render(textResult.view()));
                    printGenerationSummary(run);
                } else {
                    out.println(textResult.issues().getFirst().message());
                }
                if (run.view().ended()) return smokeResult(options, run);
                view = run.view();
                continue;
            }
            if (view.input().mode() == com.capybee.engine.api.InputMode.TEXT
                    || view.input().mode() == com.capybee.engine.api.InputMode.CHOICE_OR_TEXT) {
                StepResult textResult = run.apply(new Command.SubmitText(line));
                if (textResult.accepted()) {
                    out.println(ConsoleRenderer.render(textResult.view()));
                    printGenerationSummary(run);
                } else {
                    out.println(textResult.issues().getFirst().message());
                }
                if (run.view().ended()) return smokeResult(options, run);
                view = run.view();
                continue;
            }
            out.println("! Press Enter to continue (q to quit).");
        }
    }

    private String readLine(BufferedReader reader) {
        try {
            return reader.readLine();
        } catch (Exception exception) {
            return null;
        }
    }

    private String moveTarget(String input) {
        int separator = input.indexOf(' ');
        String command = separator < 0 ? input : input.substring(0, separator);
        if (!command.equalsIgnoreCase("move") && !command.equalsIgnoreCase("m")) return null;
        return separator < 0 ? "" : input.substring(separator + 1).trim();
    }

    private String consoleUsage() {
        return "Usage: java -jar engine-cli.jar --pack-dir <dir> [--locale <tag>] [--input <key=value>]... [--script <file>] [--generated]\n"
                + "       Add --session for resumable play; without --store-dir session data is in memory only.\n"
                + "       Session options: --store-dir <dir>, --run <id>, --new, --debug, --prefetch on|off.\n"
                + "                        --calls-per-day <0..100000>, --run-calls <1..1000>.\n"
                + "       Add --live-smoke --script <file> to call the configured provider (may incur cost).\n"
                + "       Provider configuration: GENERATION_MODEL_ID and ANTHROPIC_API_KEY environment variables.";
    }

    private int runSessionMode(ConsoleOptions options, PackId packId, com.capybee.engine.pack.Pack pack) {
        LocalSessionStore local = null;
        SessionPorts.SessionStore store;
        if (options.storeDir() == null) store = new InMemorySessionStore(clock);
        else { local = LocalSessionStore.open(options.storeDir(), clock); store = local; }
        byte[] commandKey = local == null ? new byte[32] : local.commandKey();
        SessionLimits defaults = SessionLimits.defaults();
        int callsPerDay = options.callsPerDay() == null ? defaults.profileCallsPerDay() : options.callsPerDay();
        int runCalls = options.runCalls() == null ? defaults.runCalls() : options.runCalls();
        boolean prefetch = options.prefetch() == null ? options.generated() : options.prefetch();
        SessionLimits limits = new SessionLimits(defaults.leaseDuration(), defaults.startTtl(), runCalls,
                callsPerDay, defaults.maxConcurrentGeneration(), callsPerDay, prefetch);
        SessionPorts.PackCatalog catalog = new SessionPorts.PackCatalog() {
            @Override public List<com.capybee.engine.pack.Pack> all() { return List.of(pack); }
            @Override public java.util.Optional<com.capybee.engine.pack.Pack> find(String id) {
                return pack.manifest().id().value().equals(id)
                        ? java.util.Optional.of(pack) : java.util.Optional.empty();
            }
        };
        SessionPorts.EngineFactory factory = (owner, runId, priority) -> {
            var builder = Engine.builder(new PackRegistry(List.of(pack))).clock(clock)
                    .random(Randoms.seeded(runId.getMostSignificantBits() ^ runId.getLeastSignificantBits()))
                    .ids(ids);
            if (textGenerator != null) {
                var metered = new MeteredTextGenerator(textGenerator, store,
                        new SessionTypes.LedgerScope("profile:local", limits.profileCallsPerDay()),
                        new SessionTypes.LedgerScope("global", limits.globalCallsPerDay()), LocalDate.now(clock),
                        runId, limits.runCalls(), priority, 8, 100, 4);
                builder.textGenerator(metered)
                        .inputInterpreter(new StructuredInputInterpreter(metered));
            }
            return builder.build();
        };
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            RunSessionService sessions = new RunSessionService(catalog, store, store, store, store,
                    factory, clock::instant, limits, store, workers, commandKey);
            SessionTypes.OwnerRef owner = new SessionTypes.OwnerRef("local-console");
            SessionTypes.RunSummary summary;
            if (options.runId() != null) {
                summary = sessions.get(owner, options.runId());
            } else if (!options.newRun() && store.findCurrent(owner).isPresent()) {
                summary = sessions.get(owner, store.findCurrent(owner).orElseThrow().id());
                if (options.locale() != null || !options.inputs().isEmpty() || options.seed() != null || options.generated()) {
                    out.println("Note: resuming run " + summary.runId() + "; start options are ignored. Use --new to start over.");
                }
            } else {
                if (options.newRun() && store.findCurrent(owner).isPresent()) {
                    UUID previous = store.findCurrent(owner).orElseThrow().id();
                    sessions.delete(owner, previous);
                    out.println("Discarded previous run " + previous + ".");
                }
                SessionTypes.StartRequest start = new SessionTypes.StartRequest(UUID.randomUUID(), packId,
                        options.locale() == null ? pack.manifest().defaultLocale() : options.locale(),
                        options.inputs(), options.seed(), options.generated());
                SessionTypes.StartAccepted accepted = sessions.start(owner, start, Duration.ofSeconds(8));
                summary = sessions.get(owner, accepted.runId());
            }
            summary = awaitActive(sessions, owner, summary);
            if (summary == null) return 3;
            return runSessionLoop(options, sessions, owner, summary);
        } catch (SessionException failure) {
            err.println(failure.code().name());
            err.println("  " + sessionHint(failure.code().name()));
            return 3;
        } catch (StoreException failure) {
            err.println(failure.code().name());
            err.println("  " + storeHint(failure.code()));
            return 3;
        } catch (RuntimeException failure) {
            err.println("SESSION_UNAVAILABLE");
            return 3;
        } finally {
            if (local != null) local.close();
        }
    }

    private String sessionHint(String code) {
        return switch (code) {
            case "RUN_UNRECOVERABLE" -> "The saved run cannot be continued. Start a new run with --new.";
            case "RUN_NOT_FOUND" -> "No such run for the local player. Omit --run to continue the current run.";
            case "RUN_ALREADY_ACTIVE" -> "A run is already open. Continue it or use --new.";
            case "START_REJECTED" -> "The pack or inputs were rejected. Check --pack-dir, --locale and --input.";
            case "START_TIMEOUT" -> "Starting took too long. Run again with --new.";
            default -> "Unexpected internal error. Nothing was changed.";
        };
    }

    private String storeHint(StoreException.Code code) {
        return switch (code) {
            case STORE_LOCKED -> "This store is open in another console. Close it or use another --store-dir.";
            case STORE_CORRUPT -> "The store file is damaged and was left untouched.";
            case STORE_VERSION_UNSUPPORTED -> "The store was written by a newer version.";
            case STORE_LEGACY_LAYOUT -> "Old run files found. Use a new --store-dir.";
            default -> "Could not read or write the store; nothing was changed.";
        };
    }

    private SessionTypes.RunSummary awaitActive(RunSessionService sessions, SessionTypes.OwnerRef owner,
                                                 SessionTypes.RunSummary summary) {
        boolean thinking = false;
        while (summary.status() == SessionTypes.RunStatus.PREPARING) {
            if (!thinking) {
                out.println("CapyBee is thinking…");
                thinking = true;
            } else out.print(".");
            sleep(Duration.ofMillis(250));
            summary = sessions.get(owner, summary.runId());
        }
        if (thinking) out.println();
        if (summary.status() != SessionTypes.RunStatus.ACTIVE) {
            err.println(summary.failureCode() == null ? "START_REJECTED" : summary.failureCode());
            return null;
        }
        return summary;
    }

    private int runSessionLoop(ConsoleOptions options, RunSessionService sessions, SessionTypes.OwnerRef owner,
                               SessionTypes.RunSummary initialSummary) {
        BufferedReader reader;
        try {
            reader = options.script() == null
                    ? new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    : Files.newBufferedReader(options.script(), StandardCharsets.UTF_8);
        } catch (Exception failure) {
            err.println("SCRIPT_INVALID");
            return 2;
        }
        SessionTypes.RunSummary summary = initialSummary;
        View view = summary.view();
        out.println("Run " + summary.runId());
        out.println("Running " + summary.packId().value() + " in locale " + summary.locale().value());
        out.println(ConsoleRenderer.render(view));
        printSessionDebug(options.debug(), sessions, owner, summary);
        while (!view.ended()) {
            if (options.script() == null) out.print("> ");
            String line = readLine(reader);
            if (line == null) return 0;
            if (options.script() != null) out.println("> " + line);
            String trimmed = line.trim();
            if (trimmed.equalsIgnoreCase("/quit") || trimmed.equalsIgnoreCase("/q")) return 0;
            if (trimmed.equalsIgnoreCase("/status")) {
                out.println(ConsoleRenderer.render(view));
                printSessionDebug(options.debug(), sessions, owner, summary);
                continue;
            }
            if (trimmed.startsWith("/events")) {
                long after = 0;
                try { if (trimmed.length() > 7) after = Long.parseLong(trimmed.substring(7).trim()); }
                catch (NumberFormatException ignored) { out.println("! Unknown command. Type ? for help."); continue; }
                while (true) {
                    var events = sessions.events(owner, summary.runId(), after, 100);
                    events.forEach(event -> out.println(event.seq() + " " + event.event().id()));
                    if (events.size() < 100) break;
                    after = events.get(events.size() - 1).seq();
                }
                continue;
            }
            if (trimmed.equalsIgnoreCase("/debug on")) {
                options = withDebug(options, true);
                printSessionDebug(true, sessions, owner, summary);
                continue;
            }
            if (trimmed.equalsIgnoreCase("/debug off")) {
                options = withDebug(options, false);
                continue;
            }
            if (trimmed.equals("?") || trimmed.equalsIgnoreCase("/help")) {
                out.println("/text <words>, /move <placeId>, /status, /events [afterSeq], /debug on|off, /abandon, /quit");
                continue;
            }
            Command command;
            if (trimmed.equalsIgnoreCase("/abandon") || (trimmed.equalsIgnoreCase("q") && sessionOption(trimmed, view) == null)) {
                if (options.script() == null) {
                    out.print("Abandon this run for good? (y/N) ");
                    String answer = readLine(reader);
                    if (answer == null || !(answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes"))) {
                        out.println("Cancelled.");
                        continue;
                    }
                }
                command = new Command.Abandon();
            } else if (trimmed.startsWith("/text ")) {
                command = new Command.SubmitText(line.substring(line.indexOf(' ') + 1));
            } else if (trimmed.startsWith("/move ")) {
                command = new Command.Move(trimmed.substring(6).trim());
            } else if (trimmed.startsWith("/")) {
                out.println("! Unknown command. Type ? for help.");
                continue;
            } else if (trimmed.isEmpty()) {
                command = new Command.Advance();
            } else {
                String optionId = sessionOption(trimmed, view);
                if (optionId != null) command = new Command.Choose(optionId);
                else if (view.input() != null && (view.input().mode() == com.capybee.engine.api.InputMode.TEXT
                        || view.input().mode() == com.capybee.engine.api.InputMode.CHOICE_OR_TEXT)) {
                    command = new Command.SubmitText(line);
                } else {
                    out.println("! Choose an option, or press Enter to continue.");
                    continue;
                }
            }
            SessionTypes.CommandOutcome outcome;
            try {
                outcome = sessions.command(owner, summary.runId(),
                        new SessionTypes.CommandRequest(UUID.randomUUID(), summary.revision(), command),
                        Duration.ofSeconds(8));
                if (outcome.outcome() == SessionTypes.Outcome.PROCESSING) out.println("CapyBee is thinking…");
                while (outcome.outcome() == SessionTypes.Outcome.PROCESSING) {
                    var status = sessions.commandStatus(owner, summary.runId(), outcome.commandId());
                    if (!status.processing()) { outcome = status.outcome(); break; }
                    out.print(".");
                    sleep(Duration.ofMillis(Math.max(250, Math.min(5000, status.retryAfterMs()))));
                }
                if (outcome.outcome() != SessionTypes.Outcome.PROCESSING) out.println();
            } catch (SessionException failure) {
                out.println("! " + failure.code().name() + ": " + sessionHint(failure.code().name()));
                continue;
            }
            summary = sessions.get(owner, summary.runId());
            view = summary.view();
            if (view != null) out.println(ConsoleRenderer.render(view));
            printSessionDebug(options.debug(), sessions, owner, summary);
            if (outcome.outcome() == SessionTypes.Outcome.REJECTED) {
                outcome.issues().forEach(issue -> out.println(issue.code()));
            }
        }
        return 0;
    }

    private String sessionOption(String input, View view) {
        if (view.options() == null) return null;
        try {
            int index = Integer.parseInt(input) - 1;
            if (index >= 0 && index < view.options().size()) return view.options().get(index).id();
        } catch (NumberFormatException ignored) {
            // Check ids below.
        }
        return view.options().stream().map(option -> option.id())
                .filter(id -> id.equalsIgnoreCase(input)).findFirst().orElse(null);
    }

    private void printSessionDebug(boolean debug, RunSessionService sessions, SessionTypes.OwnerRef owner,
                                   SessionTypes.RunSummary summary) {
        if (!debug) return;
        SessionTypes.Diagnostics diagnostics = sessions.diagnostics(owner, summary.runId());
        out.println("revision=" + diagnostics.revision() + " calls=" + diagnostics.generationCalls()
                + "/" + diagnostics.runCallCeiling() + " prefetchReady=" + diagnostics.readyPrefetch()
                + " prefetchInFlight=" + diagnostics.inFlightPrefetch());
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private ConsoleOptions withDebug(ConsoleOptions options, boolean debug) {
        return new ConsoleOptions(options.packDir(), options.locale(), options.inputs(), options.seed(),
                options.script(), options.save(), options.load(), options.generated(), options.liveSmoke(),
                options.help(), options.session(), options.storeDir(), options.runId(), options.newRun(), debug,
                options.prefetch(), options.callsPerDay(), options.runCalls());
    }

    private void printGenerationSummary(Run run) {
        var summary = run.generationSummary();
        if (summary.isEmpty()) return;
        List<GenerationExecutionSummary.StageExecution> stages = summary.get().stages();
        for (int index = printedGenerationStages; index < stages.size(); index++) {
            var stage = stages.get(index);
            String provider = stage.providerId() == null ? "none"
                : stage.providerId().matches("[A-Za-z0-9._:-]{1,100}") ? stage.providerId() : "unknown";
            out.println("Generation " + stage.stage() + " " + stage.outcome()
                    + " calls=" + stage.providerCalls()
                + " provider=" + provider
                + (stage.usage() == null ? "" : " usage=" + stage.usage().inputTokens() + "/" + stage.usage().outputTokens())
                    + (stage.failureCode() == null ? "" : " code=" + stage.failureCode()));
        }
        printedGenerationStages = stages.size();
    }

    private int smokeResult(ConsoleOptions options, Run run) {
        if (!options.liveSmoke()) return 0;
        GenerationExecutionSummary summary = run.generationSummary().orElse(null);
        if (summary == null || summary.totalProviderCalls() == 0
                || summary.stages().stream().anyMatch(stage -> stage.outcome() == GenerationArtifact.Outcome.FALLBACK)) {
            return smokeIncomplete();
        }
        for (GenerationCatalog.Stage required : List.of(GenerationCatalog.Stage.SCENARIO,
                GenerationCatalog.Stage.CAST, GenerationCatalog.Stage.QUEST)) {
            if (summary.stages().stream().noneMatch(stage -> stage.stage() == required && stage.providerCalls() > 0)) {
                return smokeIncomplete();
            }
        }
        long liveScenes = summary.stages().stream().filter(stage -> stage.stage() == GenerationCatalog.Stage.SCENE
                && stage.providerCalls() > 0 && stage.outcome() != GenerationArtifact.Outcome.FALLBACK).count();
        if (liveScenes < 2) return smokeIncomplete();
        out.println("Live generation smoke test passed.");
        return 0;
    }

    private int smokeIncomplete() {
        err.println("LIVE_SMOKE_INCOMPLETE: required stages and a lazily generated scene must succeed without fallback.");
        return 4;
    }
}
