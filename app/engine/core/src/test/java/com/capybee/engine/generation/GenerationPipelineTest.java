package com.capybee.engine.generation;

import com.capybee.engine.adapter.SequentialIdSource;
import com.capybee.engine.api.Engine;
import com.capybee.engine.generation.GenerationArtifact.Outcome;
import com.capybee.engine.pack.DirectoryPackSource;
import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.Pack;
import com.capybee.engine.pack.PackId;
import com.capybee.engine.pack.PackSource;
import com.capybee.engine.pack.json.PackLoader;
import com.capybee.engine.port.StructuredGenerationRequest;
import com.capybee.engine.port.TextGenerationException;
import com.capybee.engine.port.TextGenerationResult;
import com.capybee.engine.port.TextGenerator;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenerationPipelineTest {
    private static final Path PACK_PATH = Path.of("..", "fixtures", "game-packs", "fixture-generated")
            .toAbsolutePath().normalize();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void runsRequiredStagesAndLazilyCachesGeneratedScene() {
        ScriptedGenerator fake = new ScriptedGenerator(scenario(), cast(), quest(), scene("opening"));
        GenerationPipeline pipeline = pipeline(loadPack(), fake);

        var initial = pipeline.initialize();
        var cached = pipeline.resolveScene("opening-slot");

        assertThat(fake.requests).hasSize(4);
        assertThat(fake.requests).extracting(StructuredGenerationRequest::slotKey)
            .containsExactly(null, null, null, "opening-slot");
        assertThat(initial.initialScene().scene().blocks().getFirst().text())
            .isEqualTo("An Open Door\nThe observatory door opens.");
        assertThat(initial.initialScene().scene().options()).extracting(option -> option.id())
                .containsExactly("explore", "rest");
        assertThat(cached.artifact().artifactId()).isEqualTo(initial.initialScene().artifact().artifactId());
        assertThat(initial.artifacts()).extracting(artifact -> artifact.outcome())
                .containsOnly(Outcome.GENERATED);
    }

            @Test
            void validatesOptionalSceneSlotKeys() {
            assertThatThrownBy(() -> requestWithSlotKey(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Generation slot key is invalid");
            assertThatThrownBy(() -> requestWithSlotKey("x".repeat(201)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Generation slot key is invalid");
            assertThat(requestWithSlotKey("meeting-slot").slotKey()).isEqualTo("meeting-slot");
            }

    @Test
    void scenarioOutputAcceptsNarrativeBeatOutline() {
        String narrativeScenario = "{\"title\":\"The Lantern Path\",\"premise\":\"A small group follows a lantern\",\"setting\":\"An ancient forest\",\"beats\":[\"Opening: the lantern appears\",\"Exploration: they search the path\",\"Rest: they regroup by a stream\"]}";
        ScriptedGenerator fake = new ScriptedGenerator(narrativeScenario, cast(), quest(), scene("opening"));
        GenerationPipeline pipeline = pipeline(loadPack(), fake);

        var initial = pipeline.initialize();

        assertThat(initial.artifacts().getFirst().outcome()).isEqualTo(Outcome.GENERATED);
        assertThat(initial.summary().stages().getFirst().providerCalls()).isEqualTo(1);
    }

    @Test
    void questOutputAcceptsGeneratedObjectiveIdsWhenTemplateHasNoAllowlist() {
        String questPayload = "{\"objectives\":[{\"title\":\"Visit the Observatory\",\"description\":\"Walk up to the small observatory above the valley and meet Mara Ellis.\",\"objectiveId\":\"obj-visit-observatory\"},{\"title\":\"Care for the Lantern\",\"description\":\"Help Mara check the lantern and give it the small repairs it needs.\",\"objectiveId\":\"obj-care-lantern\"},{\"title\":\"Watch the Night Sky\",\"description\":\"Once the lantern glows, look up with Mara and learn one star pattern.\",\"objectiveId\":\"obj-watch-night-sky\"}]}";
        ScriptedGenerator fake = new ScriptedGenerator(scenario(), cast(), questPayload, scene("opening"));
        GenerationPipeline pipeline = pipeline(loadPack(), fake);

        var initial = pipeline.initialize();

        assertThat(initial.artifacts().get(2).outcome()).isEqualTo(Outcome.GENERATED);
        assertThat(initial.summary().stages().get(2).providerCalls()).isEqualTo(1);
    }

    @Test
    void scenarioOutputAcceptsRepeatedNarrativeBeatLabels() {
        String repeatedLabels = "{\"title\":\"The Lantern Under the Old Bridge\",\"premise\":\"When the village lantern-keeper goes missing on the night of the autumn festival, a curious young apprentice must follow a trail of glowing footprints beneath the old stone bridge to bring the town's light back.\",\"setting\":\"A quiet riverside village in autumn, with cobblestone lanes, drifting fog, a stone footbridge over a slow river, and a lantern tower that glows each night for travelers.\",\"beats\":[\"Opening: The apprentice climbs the lantern tower at dusk and finds the lantern dark and the keeper's cloak left behind on the railing.\",\"Opening: Villagers gather in the square, worried, as the festival lights flicker and fail one by one.\",\"Meeting: Beneath the old bridge, the apprentice meets a shy river spirit who has been guiding lost travelers using stolen glimmers of light.\",\"Meeting: The spirit explains that the keeper went looking for a fallen star lodged in the riverbed and has not returned.\",\"Resolution: Together they recover the star, return to the tower, and relight the lantern so the whole village shines again.\"]}";
        ScriptedGenerator fake = new ScriptedGenerator(repeatedLabels, cast(), quest(), scene("opening"));
        GenerationPipeline pipeline = pipeline(loadPack(), fake);

        var initial = pipeline.initialize();

        assertThat(initial.artifacts().getFirst().outcome()).isEqualTo(Outcome.GENERATED);
        assertThat(initial.summary().stages().getFirst().providerCalls()).isEqualTo(1);
    }

    @Test
    void restoresNarrativeScenarioBeatsWithoutTreatingThemAsSceneSlotIds() {
        String narrativeScenario = "{\"title\":\"The Lantern Path\",\"premise\":\"A small group follows a lantern\",\"setting\":\"An ancient forest\",\"beats\":[\"Opening: the lantern appears\",\"Exploration: they search the path\",\"Rest: they regroup by a stream\"]}";
        GenerationArtifact scenario = new GenerationArtifact(GenerationArtifact.SUPPORTED_SCHEMA_VERSION,
                loadPack().manifest().id(), loadPack().manifest().version(), new com.capybee.engine.pack.LocaleTag("en"),
                GenerationCatalog.Stage.SCENARIO, "scenario-basic", "scenario-artifact",
                Outcome.GENERATED, new GenerationArtifact.ScenarioPayload(
                        "The Lantern Path", "A small group follows a lantern", "An ancient forest",
                        List.of("Opening: the lantern appears", "Exploration: they search the path", "Rest: they regroup by a stream")));
        GenerationArtifact castArtifact = new GenerationArtifact(GenerationArtifact.SUPPORTED_SCHEMA_VERSION,
                loadPack().manifest().id(), loadPack().manifest().version(), new com.capybee.engine.pack.LocaleTag("en"),
                GenerationCatalog.Stage.CAST, "cast-basic", "cast-artifact",
                Outcome.GENERATED, new GenerationArtifact.CastPayload(List.of(
                        new GenerationArtifact.CastMember("guide", "actor-1", "Mira", "Warm guide"))));
        GenerationArtifact questArtifact = new GenerationArtifact(GenerationArtifact.SUPPORTED_SCHEMA_VERSION,
                loadPack().manifest().id(), loadPack().manifest().version(), new com.capybee.engine.pack.LocaleTag("en"),
                GenerationCatalog.Stage.QUEST, "quest-basic", "quest-artifact",
                Outcome.GENERATED, new GenerationArtifact.QuestPayload(List.of()));
        GenerationArtifact sceneArtifact = new GenerationArtifact(GenerationArtifact.SUPPORTED_SCHEMA_VERSION,
                loadPack().manifest().id(), loadPack().manifest().version(), new com.capybee.engine.pack.LocaleTag("en"),
                GenerationCatalog.Stage.SCENE, "scene-basic", "scene-artifact",
                Outcome.GENERATED, new GenerationArtifact.ScenePayload("opening", "The Opening",
                        "The observatory hums softly.", List.of(), List.of(
                                new GenerationArtifact.ChoiceLabel("explore", "Explore"),
                                new GenerationArtifact.ChoiceLabel("rest", "Rest"))));

        GenerationPipeline restored = GenerationPipeline.restore(loadPack(), new com.capybee.engine.pack.LocaleTag("en"),
                new ScriptedGenerator(narrativeScenario, cast(), quest(), scene("opening")),
                new com.capybee.engine.adapter.SequentialIdSource("restore"), CLOCK,
                List.of(scenario, castArtifact, questArtifact, sceneArtifact));

        assertThat(restored.artifacts()).extracting(artifact -> artifact.stage())
                .containsExactlyInAnyOrder(GenerationCatalog.Stage.SCENARIO, GenerationCatalog.Stage.CAST,
                        GenerationCatalog.Stage.QUEST, GenerationCatalog.Stage.SCENE);
    }

    @Test
    void repairsInvalidOutputOnceAndDoesNotRetainRawOutputInDiagnostics() {
        String invalid = "{\"title\":\"private-marker\",\"premise\":\"P\",\"setting\":\"S\",\"beats\":[\"opening-slot\"],\"route\":\"ending\"}";
        ScriptedGenerator fake = new ScriptedGenerator(invalid, scenario(), cast(), quest(), scene("opening"));
        GenerationPipeline pipeline = pipeline(loadPack(), fake);

        var initial = pipeline.initialize();

        assertThat(initial.artifacts().getFirst().outcome()).isEqualTo(Outcome.REPAIRED);
        assertThat(initial.summary().stages().getFirst().providerCalls()).isEqualTo(2);
        assertThat(initial.summary().stages().getFirst().failureCode()).isEqualTo("OUTPUT_SCHEMA_INVALID");
        assertThat(initial.summary().toString()).doesNotContain("private-marker");
        assertThat(fake.requests.get(1).untrustedDataJson()).contains("OUTPUT_SCHEMA_INVALID").doesNotContain("private-marker");
    }

    @Test
    void providerFailureUsesFallbackWithoutRepairAndCountsFailedCalls() {
        List<StructuredGenerationRequest> requests = new ArrayList<>();
        TextGenerator unavailable = request -> {
            requests.add(request);
            throw new TextGenerationException(TextGenerationException.Code.UNAVAILABLE);
        };
        GenerationPipeline pipeline = pipeline(loadPack(), unavailable);

        var initial = pipeline.initialize();

        assertThat(requests).hasSize(4);
        assertThat(initial.artifacts()).extracting(artifact -> artifact.outcome())
                .containsOnly(Outcome.FALLBACK);
        assertThat(initial.summary().totalProviderCalls()).isEqualTo(4);
        assertThat(initial.summary().stages()).allSatisfy(execution -> {
            assertThat(execution.providerCalls()).isEqualTo(1);
            assertThat(execution.failureCode()).isEqualTo("PROVIDER_UNAVAILABLE");
        });
    }

    @Test
    void invalidFallbackFailsConstructionBeforeCallingProvider() {
        Pack source = loadPack();
        Map<String, String> files = new LinkedHashMap<>(source.files());
        files.put("generation/fallbacks/cast.json", "{\"unexpected\":true}");
        Pack invalidPack = new Pack(source.manifest(), files);
        ScriptedGenerator fake = new ScriptedGenerator(scenario());

        assertThatThrownBy(() -> pipeline(invalidPack, fake))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("GENERATION_FALLBACK_INVALID");
        assertThat(fake.requests).isEmpty();
    }

    @Test
    void runBudgetStopsCallsAndUsesFallbackForRemainingStages() {
        Pack source = loadPack();
        Map<String, String> files = new LinkedHashMap<>(source.files());
        files.put("generation/catalog.json", files.get("generation/catalog.json")
                .replace("\"totalCallsPerRun\":16", "\"totalCallsPerRun\":2"));
        Pack limitedPack = new Pack(source.manifest(), files);
        ScriptedGenerator fake = new ScriptedGenerator(scenario(), cast());

        var initial = pipeline(limitedPack, fake).initialize();

        assertThat(fake.requests).hasSize(2);
        assertThat(initial.summary().totalProviderCalls()).isEqualTo(2);
        assertThat(initial.artifacts()).extracting(artifact -> artifact.outcome())
                .containsExactly(Outcome.GENERATED, Outcome.GENERATED, Outcome.FALLBACK, Outcome.FALLBACK);
    }

    private GenerationPipeline pipeline(Pack pack, TextGenerator generator) {
        return new GenerationPipeline(pack, new LocaleTag("en"), generator,
                new SequentialIdSource("generation-test"), CLOCK);
    }

    private StructuredGenerationRequest requestWithSlotKey(String slotKey) {
        return new StructuredGenerationRequest("request", "GEN_SCENE", "policy", "instructions", "{}",
                "{}", 100, StructuredGenerationRequest.CostClass.STANDARD, slotKey);
    }

    private Pack loadPack() {
        return new PackLoader(Engine.sectionValidators())
                .load(new PackId("fixture-generated"), new DirectoryPackSource(PACK_PATH));
    }

    private static String scenario() {
        return "{\"title\":\"A New Evening\",\"premise\":\"A lantern needs care.\",\"setting\":\"The observatory\",\"beats\":[\"opening-slot\",\"meeting-slot\"]}";
    }

    private static String cast() { return "{\"characters\":[{\"name\":\"Mira\",\"description\":\"A careful guide.\"}]}"; }
    private static String quest() { return "{\"objectives\":[]}"; }
    private static String scene(String ignored) {
        return "{\"title\":\"An Open Door\",\"narration\":\"The observatory door opens.\",\"dialogue\":[],"
                + "\"choices\":[{\"id\":\"explore\",\"label\":\"Look around\"},{\"id\":\"rest\",\"label\":\"Rest here\"}]}";
    }

    private static final class ScriptedGenerator implements TextGenerator {
        private final Deque<String> outputs = new ArrayDeque<>();
        private final List<StructuredGenerationRequest> requests = new ArrayList<>();
        private ScriptedGenerator(String... outputs) { this.outputs.addAll(List.of(outputs)); }
        @Override public TextGenerationResult generate(StructuredGenerationRequest request) {
            requests.add(request);
            return new TextGenerationResult(outputs.removeFirst(), null, "scripted-test", TextGenerationResult.FinishReason.COMPLETE);
        }
    }
}