package com.capybee.engine.cli;

import com.capybee.engine.adapter.Randoms;
import com.capybee.engine.adapter.UuidIdSource;
import com.capybee.engine.adapter.anthropic.AnthropicConfig;
import com.capybee.engine.adapter.anthropic.AnthropicTextGenerator;
import com.capybee.engine.port.IdSource;
import com.capybee.engine.port.TextGenerator;

import java.io.InputStream;
import java.io.PrintStream;
import java.time.Clock;
import java.util.random.RandomGenerator;

public final class PlayMain {
    private PlayMain() {}

    public static void main(String[] args) {
        TextGenerator generator = null;
        if (args != null && java.util.Arrays.stream(args).anyMatch(arg -> arg.equals("--generated") || arg.equals("--live-smoke"))) {
            try {
                generator = new AnthropicTextGenerator(AnthropicConfig.fromEnvironment());
            } catch (IllegalArgumentException ignored) {
                generator = null;
            }
        }
        int exitCode = new ConsoleApp(System.in, System.out, System.err,
                Clock.systemUTC(), new UuidIdSource(), Randoms.unseeded(), generator).run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}
