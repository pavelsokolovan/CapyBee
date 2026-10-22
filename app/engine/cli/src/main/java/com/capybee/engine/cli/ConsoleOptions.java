package com.capybee.engine.cli;

import com.capybee.engine.pack.LocaleTag;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

public record ConsoleOptions(Path packDir, LocaleTag locale, Map<String, String> inputs, Long seed,
                            Path script, Path save, Path load, boolean generated, boolean liveSmoke, boolean help,
                            boolean session, Path storeDir, UUID runId, boolean newRun, boolean debug,
                            Boolean prefetch, Integer callsPerDay, Integer runCalls,
                            boolean play, boolean memory, boolean classic, boolean continueRun,
                            boolean replaceCurrent, boolean noColor, boolean acceptGenerationDisclosure,
                            boolean offline) {
    public ConsoleOptions {
        inputs = inputs == null ? Map.of() : Map.copyOf(inputs);
    }

    public ConsoleOptions(Path packDir, LocaleTag locale, Map<String, String> inputs, Long seed,
                          Path script, Path save, Path load, boolean generated, boolean liveSmoke, boolean help) {
        this(packDir, locale, inputs, seed, script, save, load, generated, liveSmoke, help,
                false, null, null, false, false, null, null, null,
                false, false, false, false, false, false, false, false);
    }

    public ConsoleOptions(Path packDir, LocaleTag locale, Map<String, String> inputs, Long seed,
                          Path script, Path save, Path load, boolean generated, boolean liveSmoke, boolean help,
                          boolean session, Path storeDir, UUID runId, boolean newRun, boolean debug) {
        this(packDir, locale, inputs, seed, script, save, load, generated, liveSmoke, help,
                session, storeDir, runId, newRun, debug, null, null, null,
                false, false, false, false, false, false, false, false);
    }

    public ConsoleOptions(Path packDir, LocaleTag locale, Map<String, String> inputs, Long seed,
                          Path script, Path save, Path load, boolean help) {
        this(packDir, locale, inputs, seed, script, save, load, false, false, help);
    }
}
