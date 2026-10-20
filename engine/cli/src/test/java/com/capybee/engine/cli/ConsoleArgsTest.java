package com.capybee.engine.cli;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConsoleArgsTest {
    @Test
    void parsesAllSupportedOptionsAndPreservesInputValues() {
        var result = ConsoleArgs.parse(new String[]{"--pack-dir", "packs/demo", "--locale", "en",
                "--input", "name=relocated kid", "--seed", "42", "--script", "play.txt", "--save", "save.json"});

        assertThat(result.ok()).isTrue();
        assertThat(result.options().packDir()).isEqualTo(Path.of("packs/demo"));
        assertThat(result.options().locale().value()).isEqualTo("en");
        assertThat(result.options().inputs()).isEqualTo(Map.of("name", "relocated kid"));
        assertThat(result.options().seed()).isEqualTo(42L);
        assertThat(result.options().script()).isEqualTo(Path.of("play.txt"));
        assertThat(result.options().save()).isEqualTo(Path.of("save.json"));
    }

    @Test
    void rejectsMissingOrMalformedArguments() {
        assertThat(ConsoleArgs.parse(null).error()).isEqualTo("A pack directory is required.");
        assertThat(ConsoleArgs.parse(new String[0]).error()).isEqualTo("A pack directory is required.");
        for (String option : new String[]{"--pack-dir", "--locale", "--input", "--seed", "--script", "--save", "--load"}) {
            assertThat(ConsoleArgs.parse(new String[]{option}).ok()).isFalse();
        }
        assertThat(ConsoleArgs.parse(new String[]{"--input", "bad"}).ok()).isFalse();
        assertThat(ConsoleArgs.parse(new String[]{"--input", "=empty"}).ok()).isFalse();
        assertThat(ConsoleArgs.parse(new String[]{"--input", "empty="}).ok()).isFalse();
        assertThat(ConsoleArgs.parse(new String[]{"--seed", "not-a-number"}).error())
                .isEqualTo("Seed must be a signed 64-bit integer.");
        assertThat(ConsoleArgs.parse(new String[]{"--unknown"}).error()).isEqualTo("Unknown argument: --unknown");
    }

    @Test
    void rejectsConflictingHelpAndLoadOptions() {
        assertThat(ConsoleArgs.parse(new String[]{"--help"}).options().help()).isTrue();
        assertThat(ConsoleArgs.parse(new String[]{"--help", "--pack-dir", "packs"}).ok()).isFalse();
        assertThat(ConsoleArgs.parse(new String[]{"--load", "save.json", "--locale", "en"}).ok()).isFalse();
        assertThat(ConsoleArgs.parse(new String[]{"--load", "save.json", "--input", "x=y"}).ok()).isFalse();
        assertThat(ConsoleArgs.parse(new String[]{"--load", "save.json", "--seed", "1"}).ok()).isFalse();
    }

    @Test
    void parsesGeneratedModeAndRequiresAScriptForLiveSmoke() {
        var generated = ConsoleArgs.parse(new String[]{"--pack-dir", "packs/generated", "--generated"});
        assertThat(generated.ok()).isTrue();
        assertThat(generated.options().generated()).isTrue();
        assertThat(generated.options().liveSmoke()).isFalse();

        assertThat(ConsoleArgs.parse(new String[]{"--pack-dir", "packs/generated", "--live-smoke"}).ok()).isFalse();
        var smoke = ConsoleArgs.parse(new String[]{"--pack-dir", "packs/generated", "--live-smoke", "--script", "play.txt"});
        assertThat(smoke.ok()).isTrue();
        assertThat(smoke.options().generated()).isTrue();
        assertThat(smoke.options().liveSmoke()).isTrue();
    }

    @Test
    void parsesSessionPersistenceAndResumeOptions() {
        var runId = java.util.UUID.randomUUID();
        var result = ConsoleArgs.parse(new String[]{"--pack-dir", "packs/demo", "--session",
                "--store-dir", "runs", "--run", runId.toString(), "--debug", "--script", "play.txt"});

        assertThat(result.ok()).isTrue();
        assertThat(result.options().session()).isTrue();
        assertThat(result.options().storeDir()).isEqualTo(Path.of("runs"));
        assertThat(result.options().runId()).isEqualTo(runId);
        assertThat(result.options().debug()).isTrue();
        assertThat(result.options().script()).isEqualTo(Path.of("play.txt"));
        assertThat(ConsoleArgs.parse(new String[]{"--pack-dir", "packs/demo", "--store-dir", "runs"}).ok())
                .isFalse();
        assertThat(ConsoleArgs.parse(new String[]{"--pack-dir", "packs/demo", "--session", "--run", "bad"}).ok())
                .isFalse();
        assertThat(ConsoleArgs.parse(new String[]{"--pack-dir", "packs/demo", "--session",
                "--run", runId.toString(), "--new"}).ok()).isFalse();
    }

    @Test
    void parsesAndValidatesSessionGenerationTuning() {
        var options = ConsoleArgs.parse(new String[]{"--pack-dir", "packs/demo", "--session",
                "--prefetch", "ON", "--calls-per-day", "0", "--run-calls", "1000"});
        assertThat(options.ok()).isTrue();
        assertThat(options.options().prefetch()).isTrue();
        assertThat(options.options().callsPerDay()).isZero();
        assertThat(options.options().runCalls()).isEqualTo(1000);

        assertThat(ConsoleArgs.parse(new String[]{"--pack-dir", "packs/demo", "--prefetch", "off"}).error())
                .isEqualTo("--store-dir, --run, --new, --debug, --prefetch, --calls-per-day and --run-calls require --session.");
        assertThat(ConsoleArgs.parse(new String[]{"--session", "--prefetch", "maybe"}).error())
                .isEqualTo("--prefetch must be on or off.");
        assertThat(ConsoleArgs.parse(new String[]{"--session", "--calls-per-day", "100001"}).ok()).isFalse();
        assertThat(ConsoleArgs.parse(new String[]{"--session", "--run-calls", "0"}).ok()).isFalse();
    }
}
