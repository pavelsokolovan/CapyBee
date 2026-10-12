package com.capybee.engine.cli;

import com.capybee.engine.pack.LocaleTag;

import java.nio.file.Path;
import java.util.Map;

public record ConsoleOptions(Path packDir, LocaleTag locale, Map<String, String> inputs, Long seed,
                            Path script, Path save, Path load, boolean help) {
    public ConsoleOptions {
        inputs = inputs == null ? Map.of() : Map.copyOf(inputs);
    }
}
