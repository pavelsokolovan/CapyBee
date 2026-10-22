package com.capybee.engine.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class CertificationCommandTest {
    @TempDir Path temp;

    @Test
    void printsContentSafeHelp() {
        Result result = run("certify", "--help");
        assertThat(result.exit).isZero();
        assertThat(result.out).contains("--mode offline", "--max-cost-usd");
    }

    @Test
    void rejectsMissingAndUnknownOptions() {
        assertThat(run("certify").exit).isEqualTo(2);
        assertThat(run("certify", "--wat").err).contains("CERTIFICATION_ARGUMENT_INVALID");
        assertThat(run("certify", "--mode", "other").exit).isEqualTo(2);
        assertThat(run("certify", "--scope", "other").exit).isEqualTo(2);
        assertThat(run("certify", "--max-provider-calls", "0").exit).isEqualTo(2);
        assertThat(run("certify", "--max-provider-calls", "x").exit).isEqualTo(2);
        assertThat(run("certify", "--max-cost-usd", "0").exit).isEqualTo(2);
        assertThat(run("certify", "--max-cost-usd", "1.0000001").exit).isEqualTo(2);
        assertThat(run("certify", "--max-cost-usd", "x").exit).isEqualTo(2);
    }

    @Test
    void rejectsMissingPackBeforeCreatingProvider() throws Exception {
        Path suite = temp.resolve("suite.json");
        Files.writeString(suite, "{}");
        Result result = run("certify", "--mode", "offline", "--pack-dir", temp.resolve("missing").toString(),
                "--suite", suite.toString(), "--evidence-dir", temp.resolve("evidence").toString());
        assertThat(result.exit).isEqualTo(2);
        assertThat(result.err).contains("PACK_SOURCE_INVALID");
    }

    @Test
    void strictSuiteRejectsUnknownPropertiesAndWritesIncompleteEvidence() throws Exception {
        Path pack = Files.createDirectory(temp.resolve("pack"));
        Path suite = temp.resolve("suite.json");
        Files.writeString(suite, "{\"unknown\":true}");
        Path evidence = temp.resolve("evidence");
        Result result = run("certify", "--mode", "offline", "--pack-dir", pack.toString(),
                "--suite", suite.toString(), "--evidence-dir", evidence.toString());
        assertThat(result.exit).isEqualTo(2);
        assertThat(result.err).contains("SUITE_INVALID");
        assertThat(Files.list(evidence).anyMatch(path -> path.getFileName().toString().endsWith(".incomplete"))).isTrue();
    }

    @Test
    void liveModeRequiresCertificationProviderModeBeforeCredentials() throws Exception {
        Path pack = Files.createDirectory(temp.resolve("pack"));
        Path suite = temp.resolve("suite.json");
        Files.writeString(suite, "{}");
        Result result = run("certify", "--mode", "live", "--pack-dir", pack.toString(),
                "--suite", suite.toString(), "--evidence-dir", temp.resolve("evidence").toString(),
                "--confirm-live-provider-cost", "--pricing-file", suite.toString(),
                "--max-provider-calls", "1", "--max-cost-usd", "1.00");
        assertThat(result.exit).isEqualTo(2);
        assertThat(result.err).contains("PROVIDER_MODE_INVALID");
    }

    private Result run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exit = new CertificationCommand(new PrintStream(out), new PrintStream(err),
                Clock.fixed(Instant.parse("2026-10-22T00:00:00Z"), ZoneOffset.UTC)).run(args);
        return new Result(exit, out.toString(), err.toString());
    }

    private record Result(int exit, String out, String err) { }
}
