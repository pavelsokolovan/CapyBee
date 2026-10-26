package com.capybee.engine.generation.blueprint;

import com.capybee.engine.adapter.Randoms;
import com.capybee.engine.adapter.SequentialIdSource;
import com.capybee.engine.api.*;
import com.capybee.engine.pack.PackRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DynamicBootstrapIntegrationTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    @Test void reviewedBlueprintStartsSharedRuntimeWithValidatedFallbackPresentationAndNoFakeSnapshot() {
        var candidate = MaterializedGameCompilerTest.reviewed(MaterializedGameCompilerTest.fixture());
        Engine engine = Engine.builder(new PackRegistry(List.of()))
                .clock(Clock.fixed(Instant.parse("2026-10-26T10:00:00Z"), ZoneOffset.UTC))
                .random(Randoms.seeded(42L)).ids(new SequentialIdSource("run"))
                .dynamicBlueprints(request -> candidate).build();

        var result = engine.startDynamic(new DynamicGameBootstrapRequest(
                BlueprintGenerationServiceTest.context().request("dynamic-1", null, BlueprintCancellation.NEVER), 99L));

        assertThat(result).isInstanceOf(DynamicGameBootstrapResult.Started.class);
        var started = (DynamicGameBootstrapResult.Started) result;
        assertThat(started.issues()).extracting(Issue::code).contains("DYNAMIC_PRESENTATION_FALLBACK")
                .doesNotContain("DYNAMIC_PRESENTATION_PENDING");
        assertThat(started.run().id()).isEqualTo("run-1");
        assertThat(started.run().view().blocks()).isNotEmpty();
        assertThat(started.run().view().options()).isNotEmpty()
                .allSatisfy(option -> assertThat(option.label()).isNotBlank());
        assertThat(started.run().view().story()).isNotNull();
        assertThat(started.run().view().objectives()).allSatisfy(objective -> {
            assertThat(objective.label()).isNotBlank();
            assertThat(objective.description()).isNotBlank();
        });
        assertThat(started.run().snapshotCapability())
                .isEqualTo(SnapshotCapability.PENDING_DYNAMIC_DEFINITION_PERSISTENCE);
        assertThatThrownBy(started.run()::snapshot).isInstanceOfSatisfying(EngineCapabilityException.class,
                failure -> assertThat(failure.code()).isEqualTo("DYNAMIC_SNAPSHOT_UNAVAILABLE"));

        String firstChoice = started.definition().runtimeSceneSet().scenes()
                .get(started.definition().runtimeSceneSet().startId()).options().getFirst().id();
        assertThat(started.run().apply(new Command.Choose(firstChoice)).accepted()).isTrue();
    }

    @Test void missingCoordinatorFailsWithoutAllocatingIdentity() {
        var ids = new SequentialIdSource("run");
        Engine engine = Engine.builder(new PackRegistry(List.of()))
                .clock(Clock.systemUTC()).random(Randoms.seeded(1L)).ids(ids).build();
        var context = BlueprintGenerationServiceTest.context();

        var result = engine.startDynamic(new DynamicGameBootstrapRequest(
                context.request("dynamic-2", null, BlueprintCancellation.NEVER), null));

        assertThat(result).isEqualTo(new DynamicGameBootstrapResult.Failed("DYNAMIC_BLUEPRINT_FAILED",
                List.of(new Issue(Issue.Severity.ERROR, "DYNAMIC_BLUEPRINT_FAILED",
                        "Dynamic game startup could not complete at this stage"))));
        assertThat(ids.nextId()).isEqualTo("run-1");
    }

    @Test void startIsGeneratedAndLaterScenesRemainLazyUntilPrefetch() {
        var candidate = MaterializedGameCompilerTest.reviewed(MaterializedGameCompilerTest.fixture());
        AtomicInteger calls = new AtomicInteger();
        var generator = (com.capybee.engine.port.TextGenerator) request -> {
            calls.incrementAndGet();
            var input = JSON.readTree(request.untrustedDataJson());
            var output = JSON.createObjectNode();
            output.put("sceneId", input.get("sceneId").asString());
            output.put("title", "A bright beginning");
            output.put("narration", "The friends pause and consider what to do next.");
            var dialogue = output.putArray("dialogue");
            if (!input.get("actors").isEmpty()) {
                var line = dialogue.addObject();
                line.put("actorId", input.get("actors").get(0).get("id").asString());
                line.put("text", "We can solve this together.");
            }
            var choices = output.putArray("choices");
            for (var choice : input.get("choices")) {
                var item = choices.addObject(); item.put("id", choice.get("id").asString());
                item.put("label", "Follow " + choice.get("intent").asString());
            }
            output.putArray("continuityFacts").add("The friends agreed to help.");
            return new com.capybee.engine.port.TextGenerationResult(JSON.writeValueAsString(output),
                    new com.capybee.engine.port.TextGenerationResult.TokenUsage(10, 20, 0, 0), "fake",
                    com.capybee.engine.port.TextGenerationResult.FinishReason.COMPLETE);
        };
        Engine engine = Engine.builder(new PackRegistry(List.of())).clock(Clock.systemUTC())
                .random(Randoms.seeded(2L)).ids(new SequentialIdSource("id"))
                .textGenerator(generator).dynamicBlueprints(request -> candidate).build();

        var result = (DynamicGameBootstrapResult.Started) engine.startDynamic(new DynamicGameBootstrapRequest(
                BlueprintGenerationServiceTest.context().request("dynamic-generated", null, BlueprintCancellation.NEVER), 7L));

        assertThat(calls).hasValue(1);
        assertThat(result.issues()).extracting(Issue::code).doesNotContain("DYNAMIC_PRESENTATION_FALLBACK");
        assertThat(result.run().view().blocks()).extracting(block -> block.text()).contains("The friends pause and consider what to do next.");
        assertThat(result.run().prefetchCandidates()).isNotEmpty();
        String candidateId = result.run().prefetchCandidates().getFirst();
        assertThat(calls).hasValue(1);
        assertThat(result.run().prefetchPresentation(candidateId)).isPresent();
        assertThat(calls).hasValue(2);
        assertThat(result.run().prefetchPresentation(candidateId)).isEmpty();
        assertThat(calls).hasValue(2);
    }
}
