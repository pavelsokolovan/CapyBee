package com.capybee.server.game;

import com.capybee.engine.adapter.Randoms;
import com.capybee.engine.adapter.UuidIdSource;
import com.capybee.engine.api.Command;
import com.capybee.engine.api.Engine;
import com.capybee.engine.api.RunRequest;
import com.capybee.engine.generation.SceneWordLimits;
import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.PackRegistry;
import com.capybee.engine.pack.PackId;
import com.capybee.engine.pack.PackVersion;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ShortAuthoredPackTest {
    private static final String STORY = "scenes/pl/story.json";

    @Test void revisedTextPreservesAllMechanicsAndGenerationPolicy() {
        var id = new PackId("capybee-new-hive");
        var registry = new GameSessionConfiguration().gamePackRegistry(id.value());
        var old = registry.find(id, new PackVersion(1, 5, 0)).orElseThrow();
        var current = registry.find(id).orElseThrow();
        assertThat(current.manifest().version()).isEqualTo(new PackVersion(1, 6, 0));
        var json = new ObjectMapper();
        var previous = json.readTree(old.requireFile(STORY)).path("scenes");
        var scenes = json.readTree(current.requireFile(STORY)).path("scenes");
        assertThat(scenes.size()).isEqualTo(previous.size());
        for (int index = 0; index < scenes.size(); index++) {
            var scene = (ObjectNode) scenes.get(index);
            var prior = (ObjectNode) previous.get(index);
            String sceneId = scene.path("id").asString();
            String fallbackPath = "generation/fallbacks/pl/" + sceneId + ".json";
            var fallback = json.readTree(current.requireFile(fallbackPath));
            if (scene.path("end").asBoolean()) {
                assertThat(scene).isEqualTo(prior);
                assertThat(current.requireFile(fallbackPath)).isEqualTo(old.requireFile(fallbackPath));
            }
            for (var block : scene.path("blocks")) {
                assertThat(block.path("kind").asString()).isEqualTo("NARRATION");
                assertThat(SceneWordLimits.wordCount(block.path("text").asString())).as(sceneId).isBetween(1, 20);
            }
            assertThat(SceneWordLimits.wordCount(fallback.path("narration").asString())).as(fallbackPath).isBetween(1, 20);
            var options = scene.path("options");
            var choices = fallback.path("choices");
            assertThat(options.size()).isEqualTo(choices.size());
            for (int choice = 0; choice < options.size(); choice++) {
                var option = options.get(choice);
                assertThat(choices.get(choice).path("id")).isEqualTo(option.path("id"));
                assertThat(choices.get(choice).path("label")).isEqualTo(option.path("label"));
                assertThat(SceneWordLimits.wordCount(option.path("label").asString())).isBetween(2, 5);
            }
            scene.remove("blocks");
            prior.remove("blocks");
            assertThat(scene).as(sceneId).isEqualTo(prior);
        }
        Set<String> changed = Set.of("pack.json", STORY);
        for (String file : old.files().keySet()) {
            if (!changed.contains(file) && !file.startsWith("generation/fallbacks/pl/")) {
                assertThat(current.requireFile(file)).as(file).isEqualTo(old.requireFile(file));
            }
        }
    }

    @Test void generatedRunsUseOneFallbackBlockAndRetainedSavesResume() {
        var id = new PackId("capybee-new-hive");
        var registry = new GameSessionConfiguration().gamePackRegistry(id.value());
        var retained = registry.find(id, new PackVersion(1, 5, 0)).orElseThrow();
        var request = new RunRequest(id, new LocaleTag("pl"), Map.of(), 7L, true);
        var oldRun = engine(new PackRegistry(List.of(retained))).start(request).run();
        assertThat(oldRun.apply(new Command.Advance()).accepted()).isTrue();
        assertThat(oldRun.apply(new Command.Move("old-lantern")).accepted()).isTrue();

        var engine = engine(registry);
        var resumed = engine.resume(oldRun.snapshot());
        assertThat(resumed.ok()).isTrue();
        assertThat(resumed.run().view()).isEqualTo(oldRun.view());

        var current = engine.start(request).run();
        assertThat(current.apply(new Command.Advance()).accepted()).isTrue();
        assertThat(current.apply(new Command.Move("old-lantern")).accepted()).isTrue();
        assertThat(current.view().blocks()).hasSize(1);
        assertThat(current.view().blocks().getFirst().text()).contains("CapyBee zostawia lampion");
    }

    private static Engine engine(PackRegistry registry) {
        return Engine.builder(registry).clock(Clock.systemUTC()).ids(new UuidIdSource())
                .random(Randoms.seeded(7)).build();
    }
}
