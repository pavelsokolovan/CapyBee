package com.capybee.engine.cli;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import static org.assertj.core.api.Assertions.assertThat;

class DynamicCertificationCommandTest {
    @Test void rejectsIncompleteArgumentsWithoutLaunchingAProcess() {
        var error = new ByteArrayOutputStream();
        int exit = new DynamicCertificationCommand(new PrintStream(ByteArrayOutputStream.nullOutputStream()),
                new PrintStream(error), Clock.systemUTC()).run(new String[]{"certify", "--dynamic"});
        assertThat(exit).isEqualTo(2);
        assertThat(error.toString(StandardCharsets.UTF_8)).contains("CERTIFICATION_ARGUMENT_INVALID");
    }

    @Test void certifiesRealTwoProcessDynamicResumeWithRedactedEvidence() throws Exception {
        Path evidence = Files.createTempDirectory("dynamic-cert-evidence-");
        Path profile = Path.of("..", "generator-profiles", "generic-adventure");
        Path fixture = Path.of("..", "certification", "dynamic-v1");
        var output = new ByteArrayOutputStream();
        int exit = new DynamicCertificationCommand(new PrintStream(output), System.err, Clock.systemUTC()).run(new String[]{
                "certify", "--mode", "offline", "--dynamic", "--profile-dir", profile.toString(),
                "--fixture-dir", fixture.toString(), "--evidence-dir", evidence.toString()});
        assertThat(exit).isZero();
        assertThat(output.toString(StandardCharsets.UTF_8)).contains("source=dynamic result=PASS");
        var files = Files.list(evidence).toList();
        assertThat(files).hasSize(1);
        String json = Files.readString(files.getFirst());
        assertThat(json).contains("\"sourceKind\" : \"DYNAMIC\"", "\"restartProcesses\" : 2",
                "\"resultClass\" : \"SUCCESS\"", "\"blueprintCallsAfterResume\" : 0")
                .doesNotContain("Signal Path", "premise", "narration");
    }
}
