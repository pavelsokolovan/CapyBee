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
        Boolean prefetch = null;
        Integer callsPerDay = null;
        Integer runCalls = null;

        for (int index = 0; index < args.length; index++) {
            String arg = args[index];
            switch (arg) {
                case "--help" -> help = true;
                case "--generated" -> generated = true;
                case "--live-smoke" -> { liveSmoke = true; generated = true; }
                case "--session" -> session = true;
                case "--new" -> newRun = true;
                case "--debug" -> debug = true;
                case "--prefetch" -> {
                    if (index + 1 >= args.length) return new ParseResult(null, "Missing value for --prefetch.");
                    String value = args[++index];
                    if (value.equalsIgnoreCase("on")) prefetch = true;
                    else if (value.equalsIgnoreCase("off")) prefetch = false;
                    else return new ParseResult(null, "--prefetch must be on or off.");
                }
                case "--calls-per-day" -> {
                    if (index + 1 >= args.length) return new ParseResult(null, "Missing value for --calls-per-day.");
                    try { callsPerDay = Integer.parseInt(args[++index]); } catch (NumberFormatException e) { callsPerDay = -1; }
                    if (callsPerDay < 0 || callsPerDay > 100000)
                        return new ParseResult(null, "--calls-per-day must be an integer from 0 to 100000.");
                }
                case "--run-calls" -> {
                    if (index + 1 >= args.length) return new ParseResult(null, "Missing value for --run-calls.");
                    try { runCalls = Integer.parseInt(args[++index]); } catch (NumberFormatException e) { runCalls = -1; }
                    if (runCalls < 1 || runCalls > 1000)
                        return new ParseResult(null, "--run-calls must be an integer from 1 to 1000.");
                }
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
                || runId != null || newRun || debug || prefetch != null || callsPerDay != null || runCalls != null)) {
            return new ParseResult(null, "--help cannot be combined with other arguments.");
        }

        if (load != null && (locale != null || !inputs.isEmpty() || seed != null)) {
            return new ParseResult(null, "--load cannot be combined with --locale, --input or --seed.");
        }

        if (liveSmoke && (script == null || load != null)) {
            return new ParseResult(null, "--live-smoke requires --script and cannot be combined with --load.");
        }

        if (!session && (storeDir != null || runId != null || newRun || debug || prefetch != null || callsPerDay != null || runCalls != null)) {
            return new ParseResult(null, "--store-dir, --run, --new, --debug, --prefetch, --calls-per-day and --run-calls require --session.");
        }
        if (session && (save != null || load != null || liveSmoke)) {
            return new ParseResult(null, "--session cannot be combined with --save, --load or --live-smoke.");
        }
        if (runId != null && newRun) {
            return new ParseResult(null, "--run cannot be combined with --new.");
        }
        if (runId != null && (locale != null || !inputs.isEmpty() || seed != null || generated)) {
            return new ParseResult(null, "--run cannot be combined with --locale, --input, --seed or --generated.");
        }

        return new ParseResult(new ConsoleOptions(packDir, locale, inputs, seed, script, save, load,
            generated, liveSmoke, help, session, storeDir, runId, newRun, debug, prefetch, callsPerDay, runCalls), null);
    }
}
