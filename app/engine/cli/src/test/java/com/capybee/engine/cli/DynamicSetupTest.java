package com.capybee.engine.cli;

import com.capybee.engine.pack.LocaleTag;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicSetupTest {
    private static final Path PROFILE = Path.of("..", "generator-profiles", "generic-adventure");

    @Test void packagedProfileLoadsAndWizardKeepsSuggestionStatusOnly() throws Exception {
        var loaded = ProfileDirectory.load(PROFILE);
        assertThat(loaded.valid()).isTrue();
        var output = new ByteArrayOutputStream();
        var wizard = new DynamicSetup(new BufferedReader(new StringReader(
                "2\n\n\n\n\n\ny\nprivate-marker@example.com\ny\n")),
                new PrintStream(output, true, StandardCharsets.UTF_8));

        var result = wizard.run(loaded.profile().orElseThrow(), new LocaleTag("en"), true);

        assertThat(result.cancelled()).isFalse();
        assertThat(result.suggestion()).isEqualTo("private-marker@example.com");
        assertThat(result.toString()).doesNotContain("private-marker");
        assertThat(output.toString(StandardCharsets.UTF_8)).contains("suggestion=[provided]")
                .doesNotContain("private-marker");
    }

    @Test void backClearAndCancelRemainLocal() throws Exception {
        var profile = ProfileDirectory.load(PROFILE).profile().orElseThrow();
        var output = new ByteArrayOutputStream();
        var wizard = new DynamicSetup(new BufferedReader(new StringReader("2\nclear\nback\n/quit\n")),
                new PrintStream(output, true, StandardCharsets.UTF_8));
        assertThat(wizard.run(profile, new LocaleTag("pl"), false).cancelled()).isTrue();
    }
}
