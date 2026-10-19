package com.capybee.engine.cli;

import com.capybee.engine.pack.LocaleTag;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class ConsoleArgs {
    private ConsoleArgs() {}

    public record ParseResult(ConsoleOptions options, String error) {
        public boolean ok() {
            return options != null && error == null;
        }
    }

    public static ParseResult parse(String[] args) {
        if (args == null || args.length == 0) {
            return new ParseResult(null, "A pack directory is required.");
        }

        Path packDir = null;
        LocaleTag locale = null;
        Map<String, String> inputs = new LinkedHashMap<>();
        Long seed = null;
        Path script = null;
        Path save = null;
        Path load = null;
        boolean generated = false;
        boolean liveSmoke = false;
        boolean help = false;
        boolean session = false;
        Path storeDir = null;
        UUID runId = null;
        boolean newRun = false;
        boolean debug = false;

        for (int index = 0; index < args.length; index++) {
            String arg = args[index];
            switch (arg) {
                case "--help" -> help = true;
                case "--generated" -> generated = true;
                case "--live-smoke" -> { liveSmoke = true; generated = true; }
                case "--session" -> session = true;
                case "--new" -> newRun = true;
                case "--debug" -> debug = true;
                case "--pack-dir" -> {
                    if (index + 1 >= args.length) return new ParseResult(null, "Missing value for --pack-dir.");
                    packDir = Path.of(args[++index]);
                }
                case "--locale" -> {
                    if (index + 1 >= args.length) return new ParseResult(null, "Missing value for --locale.");
                    locale = new LocaleTag(args[++index]);
                }
                case "--input" -> {
                    if (index + 1 >= args.length) return new ParseResult(null, "Missing value for --input.");
                    String text = args[++index];
                    int equals = text.indexOf('=');
                    if (equals <= 0 || equals == text.length() - 1) {
                        return new ParseResult(null, "Start inputs must be in key=value format.");
                    }
                    inputs.put(text.substring(0, equals), text.substring(equals + 1));
                }
                case "--seed" -> {
                    if (index + 1 >= args.length) return new ParseResult(null, "Missing value for --seed.");
                    try {
                        seed = Long.parseLong(args[++index]);
                    } catch (NumberFormatException exception) {
                        return new ParseResult(null, "Seed must be a signed 64-bit integer.");
                    }
                }
                case "--script" -> {
                    if (index + 1 >= args.length) return new ParseResult(null, "Missing value for --script.");
                    script = Path.of(args[++index]);
                }
                case "--save" -> {
                    if (index + 1 >= args.length) return new ParseResult(null, "Missing value for --save.");
                    save = Path.of(args[++index]);
                }
                case "--load" -> {
                    if (index + 1 >= args.length) return new ParseResult(null, "Missing value for --load.");
                    load = Path.of(args[++index]);
                }
                case "--store-dir" -> {
                    if (index + 1 >= args.length) return new ParseResult(null, "Missing value for --store-dir.");
                    storeDir = Path.of(args[++index]);
                }
                case "--run" -> {
                    if (index + 1 >= args.length) return new ParseResult(null, "Missing value for --run.");
                    try {
                        runId = UUID.fromString(args[++index]);
                    } catch (IllegalArgumentException exception) {
                        return new ParseResult(null, "--run must be a UUID.");
                    }
                }
                default -> {
                    return new ParseResult(null, "Unknown argument: " + arg);
                }
            }
        }

        if (help && (packDir != null || locale != null || !inputs.isEmpty() || seed != null || script != null
                || save != null || load != null || generated || liveSmoke || session || storeDir != null
                || runId != null || newRun || debug)) {
            return new ParseResult(null, "--help cannot be combined with other arguments.");
        }

        if (load != null && (locale != null || !inputs.isEmpty() || seed != null)) {
            return new ParseResult(null, "--load cannot be combined with --locale, --input or --seed.");
        }

        if (liveSmoke && (script == null || load != null)) {
            return new ParseResult(null, "--live-smoke requires --script and cannot be combined with --load.");
        }

        if (!session && (storeDir != null || runId != null || newRun || debug)) {
            return new ParseResult(null, "--store-dir, --run, --new and --debug require --session.");
        }
        if (session && (save != null || load != null || liveSmoke)) {
            return new ParseResult(null, "--session cannot be combined with --save, --load or --live-smoke.");
        }
        if (runId != null && newRun) {
            return new ParseResult(null, "--run cannot be combined with --new.");
        }

        return new ParseResult(new ConsoleOptions(packDir, locale, inputs, seed, script, save, load,
            generated, liveSmoke, help, session, storeDir, runId, newRun, debug), null);
    }
}
