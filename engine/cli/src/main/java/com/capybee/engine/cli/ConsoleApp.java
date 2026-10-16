package com.capybee.engine.cli;

import com.capybee.engine.adapter.anthropic.AnthropicTextGenerator;
import com.capybee.engine.adapter.Randoms;
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

import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
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
                + "       Add --live-smoke --script <file> to call the configured provider (may incur cost).\n"
                + "       Provider configuration: GENERATION_MODEL_ID and ANTHROPIC_API_KEY environment variables.";
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
