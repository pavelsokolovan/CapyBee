package com.capybee.engine.cli;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

@FunctionalInterface
interface ConsolePaths {
    Path stateDirectory();

    static ConsolePaths system() {
        return () -> resolve(System.getenv(), System.getProperty("os.name", ""),
                Path.of(System.getProperty("user.home")));
    }

    static Path resolve(Map<String, String> environment, String osName, Path home) {
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String local = environment.get("LOCALAPPDATA");
            if (local != null && !local.isBlank()) return Path.of(local, "CapyBee", "cli");
        }
        if (os.contains("mac")) return home.resolve("Library/Application Support/CapyBee/cli");
        String xdg = environment.get("XDG_STATE_HOME");
        return xdg == null || xdg.isBlank() ? home.resolve(".local/state/capybee/cli")
                : Path.of(xdg, "capybee", "cli");
    }
}
