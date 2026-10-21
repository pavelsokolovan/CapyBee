package com.capybee.engine.generation;

import com.capybee.engine.adapter.SequentialIdSource;
import com.capybee.engine.adapter.Randoms;
import com.capybee.engine.api.Command;
import com.capybee.engine.api.Engine;
import com.capybee.engine.api.RunRequest;
import com.capybee.engine.api.json.SnapshotCodec;
import com.capybee.engine.pack.DirectoryPackSource;
import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.PackId;
import com.capybee.engine.pack.PackRegistry;
import com.capybee.engine.pack.Pack;
import com.capybee.engine.pack.json.PackLoader;
import com.capybee.engine.port.StructuredGenerationRequest;
import com.capybee.engine.port.TextGenerationResult;
import com.capybee.engine.port.TextGenerator;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class GeneratedRunIntegrationTest {
    private static final Path PACK_PATH = Path.of("..", "fixtures", "game-packs", "fixture-generated")
            .toAbsolutePath().normalize();

    @Test
    void generatedRunContinuesThroughLazySceneAndBackToAuthoredRuntime() {
        var pack = new PackLoader(Engine.sectionValidators())
                .load(new PackId("fixture-generated"), new DirectoryPackSource(PACK_PATH));
        ScriptedGenerator generator = new ScriptedGenerator(
                "{\"title\":\"An Evening\",\"premise\":\"A lantern waits.\",\"setting\":\"Observatory\",\"beats\":[\"opening-slot\",\"meeting-slot\"]}",
                "{\"characters\":[]}",
                "{\"objectives\":[]}",
                scene("opening", "A Quiet Start", "explore", "Look around", "rest", "Rest"),
                scene("meeting", "A New Room", "continue", "Continue", "finish", "Finish"));
        Engine engine = Engine.builder(new PackRegistry(List.of(pack)))
                .clock(Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC))
                .random(Randoms.seeded(17))
                .ids(new SequentialIdSource("generated-run"))
                .textGenerator(generator)
                .build();

        var start = engine.start(new RunRequest(new PackId("fixture-generated"), new LocaleTag("en"),
                java.util.Map.of(), 17L, true));
        assertThat(start.ok()).isTrue();
        assertThat(start.run().view().blocks().getFirst().text()).isEqualTo("A Quiet Start\nOpening generated.");

        var next = start.run().apply(new Command.Choose("explore"));

        assertThat(next.accepted()).isTrue();
        assertThat(next.view().blocks().getFirst().text()).isEqualTo("A New Room\nMeeting generated.");
        assertThat(start.run().snapshot().step()).isEqualTo(1);
        assertThat(generator.requests).hasSize(5);

        String snapshotJson = SnapshotCodec.toJson(start.run().snapshot());
        var parsed = SnapshotCodec.fromJson(snapshotJson);
        assertThat(parsed.issues()).isEmpty();
        assertThat(parsed.snapshot().state().generationArtifacts())
                .filteredOn(artifact -> artifact.stage() == GenerationCatalog.Stage.SCENE)
                .extracting(artifact -> ((GenerationArtifact.ScenePayload) artifact.payload()).sceneId())
                .contains("opening", "meeting");
        assertThatCode(() -> GenerationPipeline.restore(pack, new LocaleTag("en"), generator,
                new SequentialIdSource("restore-test"), Clock.systemUTC(),
                parsed.snapshot().state().generationArtifacts())).doesNotThrowAnyException();
        var resumed = engine.resume(parsed.snapshot());
        assertThat(resumed.issues()).isEmpty();
        assertThat(resumed.ok()).isTrue();
        assertThat(resumed.run().view().blocks().getFirst().text()).isEqualTo("A New Room\nMeeting generated.");
        assertThat(generator.requests).hasSize(5);

        var end = resumed.run().apply(new Command.Choose("continue"));
        assertThat(end.accepted()).isTrue();
        assertThat(end.view().blocks().getFirst().text()).isEqualTo("The evening settles peacefully.");
        assertThat(generator.requests).hasSize(5);
    }

    @Test
    void prefetchResolvesReachableGeneratedSceneWithoutChangingRunAndReusesItOnEntry() {
        var pack = new PackLoader(Engine.sectionValidators())
                .load(new PackId("fixture-generated"), new DirectoryPackSource(PACK_PATH));
        ScriptedGenerator generator = new ScriptedGenerator(
                "{\"title\":\"An Evening\",\"premise\":\"A lantern waits.\",\"setting\":\"Observatory\",\"beats\":[\"opening-slot\",\"meeting-slot\"]}",
                "{\"characters\":[]}",
                "{\"objectives\":[]}",
                scene("opening", "A Quiet Start", "explore", "Look around", "rest", "Rest"),
                scene("meeting", "A New Room", "continue", "Continue", "finish", "Finish"));
        Engine engine = Engine.builder(new PackRegistry(List.of(pack)))
                .clock(Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC))
                .random(Randoms.seeded(17))
                .ids(new SequentialIdSource("generated-run"))
                .textGenerator(generator)
                .build();

        var run = engine.start(new RunRequest(new PackId("fixture-generated"), new LocaleTag("en"),
                java.util.Map.of(), 17L, true)).run();
        var before = run.snapshot();
        var viewBefore = run.view();

        assertThat(run.prefetchCandidates()).containsExactly("meeting");
        var prefetched = run.prefetch("meeting");

        assertThat(prefetched).isPresent();
        assertThat(prefetched.orElseThrow().outcome()).isEqualTo(GenerationArtifact.Outcome.GENERATED);
        assertThat(run.snapshot().step()).isEqualTo(before.step());
        assertThat(run.snapshot().state().sceneId()).isEqualTo(before.state().sceneId());
        assertThat(run.snapshot().state().counters()).isEqualTo(before.state().counters());
        assertThat(run.snapshot().state().flags()).isEqualTo(before.state().flags());
        assertThat(run.snapshot().state().sets()).isEqualTo(before.state().sets());
        assertThat(run.snapshot().state().objectives()).isEqualTo(before.state().objectives());
        assertThat(run.snapshot().updatedAt()).isEqualTo(before.updatedAt());
        assertThat(run.view()).isEqualTo(viewBefore);
        assertThat(generator.requests).hasSize(5);

        assertThat(run.apply(new Command.Choose("explore")).accepted()).isTrue();
        assertThat(generator.requests).hasSize(5);
    }

    @Test
    void prefetchCandidatesAreEmptyForAuthoredAndEndedRunsAndDeduplicateConvergingTargets() throws Exception {
        Pack authoredPack = new PackLoader(Engine.sectionValidators()).load(new PackId("fixture-text-dungeon"),
                new DirectoryPackSource(Path.of("..", "fixtures", "game-packs", "fixture-text-dungeon")
                        .toAbsolutePath().normalize()));
        Engine authoredEngine = Engine.builder(new PackRegistry(List.of(authoredPack)))
                .clock(Clock.systemUTC()).random(Randoms.seeded(4)).ids(new SequentialIdSource("authored"))
                .build();
        var authoredRun = authoredEngine.start(new RunRequest(authoredPack.manifest().id(),
                authoredPack.manifest().defaultLocale(), java.util.Map.of(), 4L, false)).run();
        assertThat(authoredRun.prefetchCandidates()).isEmpty();

        Pack original = new PackLoader(Engine.sectionValidators()).load(new PackId("fixture-generated"),
                new DirectoryPackSource(PACK_PATH));
        Map<String, String> files = new LinkedHashMap<>(original.files());
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode story = (ObjectNode) mapper.readTree(files.get("scenes/en/story.json"));
        ArrayNode scenes = (ArrayNode) story.get("scenes");
        ObjectNode opening = (ObjectNode) scenes.get(0);
        ((ObjectNode) opening.get("options").get(1)).put("next", "meeting");
        files.put("scenes/en/story.json", mapper.writeValueAsString(story));
        Pack convergingPack = new Pack(original.manifest(), files);
        ScriptedGenerator provider = generatedProvider();
        Engine generatedEngine = Engine.builder(new PackRegistry(List.of(convergingPack)))
                .clock(Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC))
                .random(Randoms.seeded(17)).ids(new SequentialIdSource("converging"))
                .textGenerator(provider).build();
        var generatedRun = generatedEngine.start(new RunRequest(new PackId("fixture-generated"),
                new LocaleTag("en"), java.util.Map.of(), 17L, true)).run();

        assertThat(generatedRun.prefetchCandidates()).containsExactly("meeting");
        assertThat(generatedRun.prefetch("meeting")).isPresent();
        assertThat(generatedRun.prefetchCandidates()).isEmpty();
        generatedRun.apply(new Command.Abandon());
        assertThat(generatedRun.prefetchCandidates()).isEmpty();
    }

    private static ScriptedGenerator generatedProvider() {
        return new ScriptedGenerator(
                "{\"title\":\"An Evening\",\"premise\":\"A lantern waits.\",\"setting\":\"Observatory\",\"beats\":[\"opening-slot\",\"meeting-slot\"]}",
                "{\"characters\":[]}", "{\"objectives\":[]}",
                scene("opening", "A Quiet Start", "explore", "Look around", "rest", "Rest"),
                scene("meeting", "A New Room", "continue", "Continue", "finish", "Finish"));
    }

    private static String scene(String ignoredSceneId, String title, String firstId, String firstLabel,
                                String secondId, String secondLabel) {
        String narration = ignoredSceneId.equals("opening") ? "Opening generated." : "Meeting generated.";
        return "{\"title\":\"" + title + "\",\"narration\":\"" + narration + "\",\"dialogue\":[],\"choices\":["
                + "{\"id\":\"" + firstId + "\",\"label\":\"" + firstLabel + "\"},"
                + "{\"id\":\"" + secondId + "\",\"label\":\"" + secondLabel + "\"}]}";
    }

    private static final class ScriptedGenerator implements TextGenerator {
        private final Deque<String> outputs = new ArrayDeque<>();
        private final List<StructuredGenerationRequest> requests = new java.util.ArrayList<>();
        private ScriptedGenerator(String... outputs) { this.outputs.addAll(List.of(outputs)); }
        @Override public TextGenerationResult generate(StructuredGenerationRequest request) {
            requests.add(request);
            return new TextGenerationResult(outputs.removeFirst(), null, "scripted-test", TextGenerationResult.FinishReason.COMPLETE);
        }
    }
}