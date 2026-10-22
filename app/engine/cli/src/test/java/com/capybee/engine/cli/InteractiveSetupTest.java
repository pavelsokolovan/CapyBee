package com.capybee.engine.cli;

import com.capybee.engine.api.Engine;
import com.capybee.engine.pack.DirectoryPackSource;
import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.PackId;
import com.capybee.engine.pack.json.PackLoader;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InteractiveSetupTest {
    private static final Path PACK = Path.of("..", "reference-game-packs", "lanterns-of-willow-marsh");

    @Test
    void fallbackUsesLocalizedDefaultsAndSkipsProviderOnlyFreeText() {
        var execution = run("\n\n\ny\n", false);

        assertThat(execution.result.cancelled()).isFalse();
        assertThat(execution.result.inputs()).containsEntry("story-tone", "cozy")
                .containsEntry("companion-focus", "balanced").doesNotContainKey("story-wish");
        assertThat(execution.output).contains("AI text service unavailable", "Story tone", "Cozy",
                "Story interests", "Companion focus", "Review your setup")
                .doesNotContain("Story wish", "may be sent to that service");
    }

    @Test
    void providerDisclosureDefaultsToNoAndCollectsNothing() {
        var execution = run("\n", true);

        assertThat(execution.result.cancelled()).isTrue();
        assertThat(execution.result.inputs()).isEmpty();
        assertThat(execution.output).contains("Continue? [y/N]", "Cancel sends nothing")
                .doesNotContain("Story tone");
    }

    @Test
    void supportsRetryBackMultiSelectAndPrivacySafeReview() {
        var execution = run("y\n9\n2\n1,2,3\n1,2\nb\n1,2\n\nA lantern near home\ny\n", true);

        assertThat(execution.result.cancelled()).isFalse();
        assertThat(execution.result.inputs()).containsEntry("story-tone", "adventurous")
                .containsEntry("interests", "animals,puzzles")
                .containsEntry("story-wish", "A lantern near home");
        assertThat(execution.output).contains("That answer is not valid", "[provided]")
                .doesNotContain("A lantern near home");
    }

    private static Execution run(String input, boolean provider) {
        var loaded = new PackLoader(Engine.sectionValidators()).tryLoad(new PackId("lanterns-of-willow-marsh"),
                new DirectoryPackSource(PACK));
        var pack = loaded.pack().orElseThrow();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        var setup = new InteractiveSetup(new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8)),
                new PrintStream(bytes, true, StandardCharsets.UTF_8));
        return new Execution(setup.run(pack, new LocaleTag("en"), provider, Map.of()),
                bytes.toString(StandardCharsets.UTF_8));
    }

    private record Execution(InteractiveSetup.Result result, String output) { }
}
