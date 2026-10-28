package com.capybee.engine.generation;

import com.capybee.engine.api.Engine;
import com.capybee.engine.api.Command;
import com.capybee.engine.api.RunRequest;
import com.capybee.engine.api.json.SnapshotCodec;
import com.capybee.engine.adapter.Randoms;
import com.capybee.engine.adapter.SequentialIdSource;
import com.capybee.engine.pack.DirectoryPackSource;
import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.PackId;
import com.capybee.engine.pack.PackRegistry;
import com.capybee.engine.pack.json.PackLoader;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
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

    @Test
    void offlinePartialRouteResumesFromCanonicalSnapshotWithoutProvider() {
        var pack = new PackLoader(Engine.sectionValidators())
                .load(new PackId("lanterns-of-willow-marsh"), new DirectoryPackSource(PACK));
        Engine first = Engine.builder(new PackRegistry(List.of(pack))).clock(Clock.systemUTC())
                .random(Randoms.seeded(27_013L)).ids(new SequentialIdSource("willow-first")).build();
        var started = first.start(new RunRequest(pack.manifest().id(), new LocaleTag("en"), Map.of(
                "story-tone", "adventurous", "interests", "puzzles", "companion-focus", "inventor"),
                null, true));
        assertThat(started.ok()).isTrue();
        var run = started.run();
        for (String choice : List.of("inspect-orchard", "trace-wheel-marks", "inspect-lens-first",
                "follow-the-diagram")) {
            assertThat(run.apply(new Command.Choose(choice)).accepted()).isTrue();
        }

        var decoded = SnapshotCodec.fromJson(SnapshotCodec.toJson(run.snapshot()));
        assertThat(decoded.issues()).isEmpty();
        Engine second = Engine.builder(new PackRegistry(List.of(pack))).clock(Clock.systemUTC())
                .random(Randoms.seeded(27_014L)).ids(new SequentialIdSource("willow-second")).build();
        var resumed = second.resume(decoded.snapshot());

        assertThat(resumed.issues()).extracting(com.capybee.engine.api.Issue::code).isEmpty();
        assertThat(resumed.ok()).isTrue();
        assertThat(resumed.run().snapshot().state().sceneId()).isEqualTo("wind-gate");
    }
}
