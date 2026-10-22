package com.capybee.engine.cli;

import com.capybee.engine.adapter.Randoms;
import com.capybee.engine.adapter.UuidIdSource;
import com.capybee.engine.adapter.anthropic.AnthropicConfig;
import com.capybee.engine.adapter.anthropic.AnthropicTextGenerator;
import com.capybee.engine.port.IdSource;
import com.capybee.engine.port.TextGenerator;

import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.random.RandomGenerator;

public final class PlayMain {
    private PlayMain() {}

    public static void main(String[] args) {
        System.setProperty("stdout.encoding", StandardCharsets.UTF_8.name());
        System.setProperty("stderr.encoding", StandardCharsets.UTF_8.name());
        System.setProperty("sun.stdout.encoding", StandardCharsets.UTF_8.name());
        System.setProperty("sun.stderr.encoding", StandardCharsets.UTF_8.name());
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(System.err, true, StandardCharsets.UTF_8);
        if (args != null && args.length > 0 && "certify".equals(args[0])) {
            int exitCode = new CertificationCommand(out, err, Clock.systemUTC()).run(args);
            if (exitCode != 0) System.exit(exitCode);
            return;
        }
        TextGenerator generator = null;
        boolean offline = args != null && java.util.Arrays.asList(args).contains("--offline");
        ProviderMode providerMode = ProviderMode.fromEnvironment();
        if (!offline && providerMode == ProviderMode.ENABLED && args != null
                && java.util.Arrays.stream(args).anyMatch(arg -> arg.equals("--generated") || arg.equals("--live-smoke"))) {
            try {
                generator = new AnthropicTextGenerator(AnthropicConfig.fromEnvironment());
            } catch (IllegalArgumentException ignored) {
                generator = null;
            }
        }
        int exitCode = new ConsoleApp(System.in, out, err,
                Clock.systemUTC(), new UuidIdSource(), Randoms.unseeded(), generator).run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    enum ProviderMode {
        DISABLED, ENABLED, CERTIFICATION;

        static ProviderMode fromEnvironment() {
            String value = System.getenv("GENERATION_PROVIDER_MODE");
            if (value == null || value.isBlank()) return ENABLED;
            return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "enabled" -> ENABLED;
                case "certification" -> CERTIFICATION;
                default -> DISABLED;
            };
        }
    }
}
