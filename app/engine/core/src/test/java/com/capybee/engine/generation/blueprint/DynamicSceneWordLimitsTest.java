package com.capybee.engine.generation.blueprint;

import com.capybee.engine.adapter.Randoms;
import com.capybee.engine.adapter.SequentialIdSource;
import com.capybee.engine.api.Command;
import com.capybee.engine.api.DynamicGameBootstrapRequest;
import com.capybee.engine.api.DynamicGameBootstrapResult;
import com.capybee.engine.api.Engine;
import com.capybee.engine.api.json.SnapshotCodec;
import com.capybee.engine.generation.GenerationArtifact.Outcome;
import com.capybee.engine.generation.SceneWordLimits;
import com.capybee.engine.generation.profile.PreparationPresetResolver;
import com.capybee.engine.generation.profile.json.GeneratorProfileLoader;
import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.PackRegistry;
import com.capybee.engine.port.StructuredGenerationRequest;
import com.capybee.engine.port.TextGenerator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicSceneWordLimitsTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String TOO_LONG = "Iskra ".repeat(20) + "migocze.";
    private static final String ACCEPTED = "Latarnia ociepla drogę do domu.";
    private static final LocaleTag POLISH = new LocaleTag("pl");

    @Test void blueprintRepairReceivesPathsAndSceneFallbackUsesPersistedPolicyAfterResume() throws Exception {
        var request = request();
        var valid = blueprint();
        var invalid = valid.deepCopy();
        ((ObjectNode) invalid.path("scenes").get(0).path("choices").get(0)).put("intent", "Dalej");
        ((ObjectNode) invalid.path("scenes").get(7)).put("intent", TOO_LONG);
        var blueprintRequests = new ArrayList<StructuredGenerationRequest>();
        var blueprintGenerator = service(providerRequest -> {
            blueprintRequests.add(providerRequest);
            return BlueprintGenerationServiceTest.complete(blueprintRequests.size() == 1 ? invalid.toString() : valid.toString());
        }, new DefaultBlueprintOutputReviewer());
        var started = (DynamicGameBootstrapResult.Started) engine(
                providerRequest -> BlueprintGenerationServiceTest.complete(presentation(providerRequest, ACCEPTED)))
                .dynamicBlueprints(blueprintGenerator)
                .build().startDynamic(new DynamicGameBootstrapRequest(request, 7L));
        assertThat(blueprintRequests).hasSize(2).allSatisfy(sent -> assertThat(sent.taskInstructions()).contains("20 words", "2–5 words"));
        assertThat(blueprintRequests.getLast().untrustedDataJson())
                .contains(SceneWordLimits.CHOICE_RULE, "/scenes/0/choices/0/intent",
                        SceneWordLimits.BLOCK_RULE, "/scenes/7/intent").doesNotContain(TOO_LONG, "Dalej");
        assertThat(started.run().view().blocks()).anySatisfy(block -> assertThat(block.text()).isEqualTo(ACCEPTED));

        var sceneRequests = new ArrayList<StructuredGenerationRequest>();
        var fresh = engine(sent -> {
            sceneRequests.add(sent);
            return BlueprintGenerationServiceTest.complete(presentation(sent, TOO_LONG));
        }).build();
        var stored = SnapshotCodec.fromJson(SnapshotCodec.toJson(started.run().snapshot()));
        assertThat(stored.issues()).isEmpty();
        var resumed = fresh.resume(stored.snapshot()).run();
        assertThat(resumed.view()).isEqualTo(started.run().view());
        assertThat(sceneRequests).isEmpty();

        var before = resumed.view();
        var next = resumed.prefetchCandidates().getFirst();
        assertThat(resumed.prefetchPresentation(next)).isPresent();
        assertThat(resumed.view()).isEqualTo(before);
        assertThat(sceneRequests).hasSize(2).allSatisfy(sent -> assertThat(sent.taskInstructions()).contains("20 words", "2–5 words"));
        assertThat(sceneRequests.getLast().untrustedDataJson()).contains(SceneWordLimits.BLOCK_RULE, "scene.narration").doesNotContain(TOO_LONG);
        assertThat(resumed.apply(new Command.Choose(before.options().getFirst().id())).accepted()).isTrue();
        assertThat(resumed.view().blocks()).anySatisfy(block -> assertThat(block.text()).isEqualTo("Przyjaciele wybierają następny krok."));
        assertThat(resumed.view().options()).anySatisfy(option -> assertThat(option.label()).isEqualTo("Ruszam dalej"));
        assertThat(resumed.generationSummary().orElseThrow().stages())
                .anySatisfy(stage -> assertThat(stage.outcome()).isEqualTo(Outcome.FALLBACK));
        assertThat(SnapshotCodec.toJson(resumed.snapshot())).doesNotContain(TOO_LONG);
        assertThat(sceneRequests).hasSize(2);
    }

    @Test void invalidMaterializedFallbackPreventsPublicationEvenIfReviewWasBypassed() throws Exception {
        var valid = blueprint();
        ((ObjectNode) valid.path("scenes").get(0)).put("intent", TOO_LONG);
        var reviewBypass = service(sent -> BlueprintGenerationServiceTest.complete(valid.toString()),
                (result, preferences, policy) -> BlueprintOutputReviewer.Review.pass("fixture"));
        var result = engine(sent -> { throw new AssertionError("fallback must validate before scene generation"); })
                .dynamicBlueprints(reviewBypass).build().startDynamic(new DynamicGameBootstrapRequest(request(), 7L));
        assertThat(result).isInstanceOfSatisfying(DynamicGameBootstrapResult.Failed.class,
                failed -> assertThat(failed.code()).isEqualTo("DYNAMIC_PRESENTATION_FALLBACK_INVALID"));
    }

    @Test void profileRejectsInvalidWordLimits() throws Exception {
        var files = profileFiles();
        var quality = (ObjectNode) JSON.readTree(files.get("quality/pl.json"));
        ((ObjectNode) quality.get("sceneWordLimits")).put("minChoiceWords", 6);
        files.put("quality/pl.json", JSON.writeValueAsBytes(quality));
        assertThat(new GeneratorProfileLoader().load(files).issues()).anySatisfy(issue -> {
            assertThat(issue.resource()).isEqualTo("quality/pl.json");
            assertThat(issue.pointer()).isEqualTo("/sceneWordLimits");
        });
    }

    private static BlueprintGenerationRequest request() throws Exception {
        var profile = new GeneratorProfileLoader().load(profileFiles()).profile().orElseThrow();
        var resolved = new PreparationPresetResolver().resolve(profile, Map.of(), POLISH, null);
        return new BlueprintGenerationRequest("polish-fixture", 1, 1, profile,
                resolved.preferences().orElseThrow(), resolved.constraints().orElseThrow(),
                null, null, BlueprintCancellation.NEVER, null);
    }

    private static Map<String, byte[]> profileFiles() throws Exception {
        Path root = Path.of("../generator-profiles/generic-adventure");
        var files = new LinkedHashMap<String, byte[]>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                files.put(root.relativize(path).toString().replace('\\', '/'), Files.readAllBytes(path));
            }
        }
        return files;
    }

    private static ObjectNode blueprint() throws Exception {
        var node = (ObjectNode) JSON.readTree(Files.readString(Path.of("../certification/dynamic-v1/blueprint.json")));
        node.put("locale", "pl");
        var scene = (ObjectNode) node.path("scenes").get(1);
        scene.put("intent", "Przyjaciele wybierają następny krok.");
        ((ObjectNode) scene.path("choices").get(0)).put("intent", "Ruszam dalej");
        return node;
    }

    private static String presentation(StructuredGenerationRequest request, String narration) {
        var input = JSON.readTree(request.untrustedDataJson());
        var output = JSON.createObjectNode().put("sceneId", input.path("sceneId").asString())
                .put("title", "Droga pod lasem").put("narration", narration);
        output.putArray("dialogue");
        var choices = output.putArray("choices");
        input.path("choices").forEach(choice -> choices.addObject()
                .put("id", choice.path("id").asString()).put("label", choice.path("intent").asString()));
        output.putArray("continuityFacts");
        return output.toString();
    }

    private static BlueprintGenerationService service(TextGenerator generator, BlueprintOutputReviewer reviewer) {
        return new BlueprintGenerationService(generator, reviewer, new DefaultBlueprintSemanticValidator(),
                new SequentialIdSource("blueprint"));
    }

    private static com.capybee.engine.api.EngineBuilder engine(TextGenerator generator) {
        return Engine.builder(new PackRegistry(List.of())).clock(Clock.systemUTC())
                .random(Randoms.seeded(7)).ids(new SequentialIdSource("run")).textGenerator(generator);
    }
}
