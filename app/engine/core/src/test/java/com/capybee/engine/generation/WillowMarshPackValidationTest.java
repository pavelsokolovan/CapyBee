package com.capybee.engine.generation;

import com.capybee.engine.api.Engine;
import com.capybee.engine.pack.DirectoryPackSource;
import com.capybee.engine.pack.PackId;
import com.capybee.engine.pack.json.PackLoader;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WillowMarshPackValidationTest {
    private static final Path PACK = Path.of("..", "reference-game-packs", "lanterns-of-willow-marsh")
            .toAbsolutePath().normalize();
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void validatesTheReferenceFullGameContract() {
        var loaded = new PackLoader(Engine.sectionValidators())
                .load(new PackId("lanterns-of-willow-marsh"), new DirectoryPackSource(PACK));
        assertThat(loaded.generatedGameProfile()).hasValueSatisfying(profile -> {
            assertThat(profile.kind()).isEqualTo(GeneratedGameProfile.Kind.FULL_GAME);
            assertThat(profile.phases()).hasSize(5);
            assertThat(profile.reachableStoryScenes()).isEqualTo(12);
            assertThat(profile.endings()).isEqualTo(3);
            assertThat(profile.minimumStoryTransitions()).isEqualTo(8);
            assertThat(profile.maximumStoryTransitions()).isEqualTo(8);
            assertThat(profile.minimumRequiredProviderCalls()).isEqualTo(13);
        });
    }

    @Test
    void hasTwelveSlotsAllFallbacksAndEveryResultClass() {
        var source = new DirectoryPackSource(PACK);
        JsonNode catalog = JSON.readTree(source.read("generation/catalog.json").orElseThrow());
        JsonNode rules = JSON.readTree(source.read("rules.json").orElseThrow());
        assertThat(catalog.get("sceneSlots")).hasSize(12);
        catalog.get("sceneSlots").forEach(slot ->
                assertThat(source.read(slot.get("fallbackResource").asString())).isPresent());
        Set<String> classes = new HashSet<>();
        rules.get("outcomes").forEach(outcome -> classes.add(outcome.get("resultClass").asString()));
        assertThat(classes).containsExactlyInAnyOrder("SUCCESS", "PARTIAL_SUCCESS", "FAILURE");
    }

    @Test
    void routesPersonalizationOnlyToInitialNarrativeStages() {
        JsonNode setup = JSON.readTree(new DirectoryPackSource(PACK)
                .read("generation/setup.json").orElseThrow());
        setup.get("questions").forEach(question -> question.get("stages").forEach(stage ->
                assertThat(stage.asString()).isIn("THEME", "SCENARIO")));
    }
}
