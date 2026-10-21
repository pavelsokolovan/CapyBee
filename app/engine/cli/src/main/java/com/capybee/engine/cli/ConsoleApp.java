package com.capybee.engine.cli;

import com.capybee.engine.adapter.anthropic.AnthropicTextGenerator;
import com.capybee.engine.api.Engine;
import com.capybee.engine.pack.DirectoryPackSource;
import com.capybee.engine.pack.LoadResult;
import com.capybee.engine.pack.PackId;
import com.capybee.engine.pack.StartInputDef;
import com.capybee.engine.pack.json.PackLoader;
import com.capybee.engine.port.IdSource;
import com.capybee.engine.port.TextGenerator;

import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.random.RandomGenerator;

public final class ConsoleApp {
    private final InputStream in;
    private final PrintStream out;
    private final PrintStream err;
    private final Clock clock;
    private final IdSource ids;
    private final RandomGenerator random;
    private final TextGenerator textGenerator;
    private final ConsoleTuning consoleTuning;
    private final ConsolePaths consolePaths;

    public ConsoleApp(InputStream in, PrintStream out, PrintStream err, Clock clock, IdSource ids, RandomGenerator random) {
        this(in, out, err, clock, ids, random, null);
    }

    public ConsoleApp(InputStream in, PrintStream out, PrintStream err, Clock clock, IdSource ids,
                      RandomGenerator random, TextGenerator textGenerator) {
        this(in, out, err, clock, ids, random, textGenerator, ConsoleTuning.defaults(), ConsolePaths.system());
    }

    ConsoleApp(InputStream in, PrintStream out, PrintStream err, Clock clock, IdSource ids,
               RandomGenerator random, TextGenerator textGenerator, ConsoleTuning consoleTuning) {
        this(in, out, err, clock, ids, random, textGenerator, consoleTuning, ConsolePaths.system());
    }

    ConsoleApp(InputStream in, PrintStream out, PrintStream err, Clock clock, IdSource ids,
               RandomGenerator random, TextGenerator textGenerator, ConsoleTuning consoleTuning,
               ConsolePaths consolePaths) {
        this.in = in;
        this.out = out;
        this.err = err;
        this.clock = clock;
        this.ids = ids;
        this.random = random;
        this.textGenerator = textGenerator;
        this.consoleTuning = consoleTuning;
        this.consolePaths = consolePaths;
    }

    public int run(String[] args) {
        ConsoleArgs.ParseResult parsed = ConsoleArgs.parse(args);
        if (!parsed.ok()) {
            err.println(parsed.error());
            return 2;
        }

        ConsoleOptions options = parsed.options();
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
        var suppliedInputs = options.inputs();
        if (options.generated() && pack.generatedGameProfile()
                .filter(profile -> profile.kind() == com.capybee.engine.generation.GeneratedGameProfile.Kind.FULL_GAME)
                .isPresent()) {
            boolean commandLineText = pack.manifest().startInputs().stream()
                    .filter(StartInputDef.FreeTextInput.class::isInstance)
                    .anyMatch(input -> suppliedInputs.containsKey(input.id()));
            if (commandLineText) {
                err.println("FREE_TEXT_COMMAND_LINE_FORBIDDEN: enter optional story text only in interactive setup.");
                return 2;
            }
        }
        if (options.play() && options.storeDir() == null && !options.memory()) {
            try {
                options = withStoreDir(options, consolePaths.stateDirectory().toAbsolutePath().normalize());
            } catch (RuntimeException failure) {
                err.println("STORE_UNAVAILABLE: Could not resolve the default save directory. Use --store-dir <directory> or --memory.");
                return 3;
            }
        }
        if (options.session()) return runSessionMode(options, packId, pack);
        return new ClassicConsole(in, out, err, clock, ids, random, textGenerator).run(options, packId, pack);
    }

    private String consoleUsage() {
        return "Usage: java -jar engine-cli.jar play --pack-dir <dir> [--locale <tag>] [--generated] [options]\n"
                + "       Normal play is saved automatically. Use --memory for temporary play.\n"
                + "       Legacy: java -jar engine-cli.jar --pack-dir <dir> [--script <file>] [--generated]\n"
                + "       Add --session for resumable legacy play; without --store-dir session data is in memory only.\n"
                + "       Session options: --store-dir <dir>, --run <id>, --new, --debug, --prefetch on|off.\n"
                + "                        --calls-per-day <0..100000>, --run-calls <1..1000>.\n"
                + "       Add --live-smoke --script <file> to call the configured provider (may incur cost).\n"
                + "       Full-game free text is accepted only by interactive setup, never by --input or scripts.\n"
                + "       Provider configuration: GENERATION_MODEL_ID and ANTHROPIC_API_KEY environment variables.";
    }

    private ConsoleOptions withStoreDir(ConsoleOptions value, Path storeDir) {
        return new ConsoleOptions(value.packDir(), value.locale(), value.inputs(), value.seed(), value.script(),
                value.save(), value.load(), value.generated(), value.liveSmoke(), value.help(), value.session(),
                storeDir, value.runId(), value.newRun(), value.debug(), value.prefetch(), value.callsPerDay(),
                value.runCalls(), value.play(), value.memory(), value.classic(), value.continueRun(),
                value.replaceCurrent(), value.noColor(), value.acceptGenerationDisclosure());
    }

    private int runSessionMode(ConsoleOptions options, PackId packId, com.capybee.engine.pack.Pack pack) {
        return SessionWiring.run(options, packId, pack, in, out, err, clock, ids, random,
                textGenerator, consoleTuning);
    }
}
