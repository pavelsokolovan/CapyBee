package com.capybee.engine.generation;

import com.capybee.engine.adapter.Randoms;
import com.capybee.engine.adapter.SequentialIdSource;
import com.capybee.engine.api.Command;
import com.capybee.engine.api.Engine;
import com.capybee.engine.api.RunRequest;
import com.capybee.engine.generation.GenerationArtifact.ChoiceLabel;
import com.capybee.engine.generation.GenerationArtifact.Outcome;
import com.capybee.engine.generation.json.GenerationQualityProfileParser;
import com.capybee.engine.pack.DirectoryPackSource;
import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.Pack;
import com.capybee.engine.pack.PackId;
import com.capybee.engine.pack.PackRegistry;
import com.capybee.engine.pack.PackSource;
import com.capybee.engine.pack.json.PackLoader;
import com.capybee.engine.port.TextGenerationResult;
import com.capybee.engine.port.TextGenerator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SceneWordLimitsTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final PackId HIVE = new PackId("capybee-new-hive");
    private static final LocaleTag POLISH = new LocaleTag("pl");
    private static final String LANTERN = "generation/fallbacks/pl/lantern.json";
    private static final String POLICY = "generation/quality/pl.json";
    private static final String VALID = "Pod oknem wisi mały lampion. W ciepłym blasku migoczą drobne pyłki, a przez uchylone drzwi wpada zapach mokrych, zielonych liści.";
    private static final String REJECTED = VALID + " Iskra.";

    @Test void countsUnicodeWordsAndReportsOnlyTrustedMetadata() {
        var limits = new SceneWordLimits(20, 2, 5);
        String examples = "🏮 Łódź,źdźbło\npolsko‑angielski słowo’słowo słowo'słowo ١٢ 🎵!";
        assertThat(SceneWordLimits.wordCount(examples)).isEqualTo(6);
        assertThat(SceneWordLimits.wordCount(Normalizer.normalize(examples, Normalizer.Form.NFD))).isEqualTo(6);
        assertThat(SceneWordLimits.wordCount(VALID)).isEqualTo(20);
        assertThat(limits.evaluate(VALID, List.of(VALID),
                List.of(new ChoiceLabel("a", "🏮 Zabieram światło"), new ChoiceLabel("b", "A b c d e")))).isEmpty();
        var findings = limits.evaluate(REJECTED, List.of(VALID, REJECTED),
                List.of(new ChoiceLabel("a", "🏮 Latarnia"), new ChoiceLabel("b", "A b c d e f")));
        assertThat(findings).extracting(GenerationQualityEvaluator.QualityFinding::scope)
                .containsExactly("scene.narration", "scene.dialogue[1].text", "scene.choices[0].label", "scene.choices[1].label");
        assertThat(findings.getFirst().measures()).containsEntry("words", 21).containsEntry("maxWords", 20);
        assertThat(findings.toString()).doesNotContain(REJECTED, "Latarnia");
    }

    @Test void validatesOptInConfigurationAndInvalidFallbackLocation() {
        var current = pack("1.5.0");
        var old = pack("1.4.0");
        var policy = (ObjectNode) JSON.readTree(current.requireFile(POLICY));
        assertThat(GenerationQualityProfileParser.parse(POLISH, policy.toString()).sceneWordLimits())
                .isEqualTo(new SceneWordLimits(20, 2, 5));
        assertThat(GenerationQualityProfileParser.parse(POLISH, old.requireFile(POLICY)).sceneWordLimits()).isNull();
        for (String malformed : List.of("null", "{}", "{\"maxBlockWords\":20.0,\"minChoiceWords\":2,\"maxChoiceWords\":5}",
                "{\"maxBlockWords\":101,\"minChoiceWords\":2,\"maxChoiceWords\":5}",
                "{\"maxBlockWords\":20,\"minChoiceWords\":6,\"maxChoiceWords\":5}",
                "{\"maxBlockWords\":20,\"minChoiceWords\":2,\"maxChoiceWords\":5,\"extra\":1}")) {
            policy.set("sceneWordLimits", JSON.readTree(malformed));
            assertThatThrownBy(() -> GenerationQualityProfileParser.parse(POLISH, policy.toString()))
                    .hasMessage("SCENE_WORD_LIMITS_INVALID");
        }
        var invalid = new PackLoader(Engine.sectionValidators()).tryLoad(HIVE, new PackSource() {
            @Override public Optional<String> read(String path) {
                return path.equals(LANTERN) ? Optional.of(narration(current, LANTERN, REJECTED)) : hiveSource("1.5.0").read(path);
            }
            @Override public String describe() { return "invalid lantern fallback"; }
        });
        assertThat(invalid.pack()).isEmpty();
        assertThat(invalid.issues()).anySatisfy(issue -> {
            assertThat(issue.file()).isEqualTo(LANTERN);
            assertThat(issue.pointer()).isEqualTo("scene.narration");
            assertThat(issue.message()).contains(SceneWordLimits.BLOCK_RULE).doesNotContain(REJECTED);
        });
    }

    @Test void packPrefetchRepairsOverlongTextAndPreservesTrustedActions() {
        var pack = pack("1.5.0");
        var savedBoard = engine(pack, null).start(new RunRequest(HIVE, POLISH, Map.of(), 7L, true)).run();
        assertThat(savedBoard.apply(new Command.Advance()).accepted()).isTrue();
        var requests = new ArrayList<com.capybee.engine.port.StructuredGenerationRequest>();
        var run = engine(pack, request -> {
            requests.add(request);
            return complete(narration(pack, LANTERN, requests.size() == 1 ? REJECTED : VALID));
        }).resume(savedBoard.snapshot()).run();
        var before = run.view();
        var prefetched = run.prefetch("lantern").orElseThrow();
        assertThat(prefetched.outcome()).isEqualTo(Outcome.REPAIRED);
        assertThat(run.view()).isEqualTo(before);
        assertThat(requests).hasSize(2).allSatisfy(request -> assertThat(request.taskInstructions()).contains("20 words", "2–5 words"));
        assertThat(requests.getLast().untrustedDataJson()).contains(SceneWordLimits.BLOCK_RULE, "scene.narration").doesNotContain(REJECTED);
        assertThat(run.apply(new Command.Move("old-lantern")).accepted()).isTrue();
        assertThat(run.view().blocks()).anySatisfy(block -> assertThat(block.text()).endsWith(VALID));
        assertThat(run.view().options()).extracting(option -> option.id()).containsExactly("carry-light", "back-to-board", "rest-now");
        assertThat(run.apply(new Command.Choose("carry-light")).accepted()).isTrue();
        assertThat(requests).hasSize(2);
    }

    @Test void packUsesWholeFallbackAfterFailedRepairAndWhenBudgetIsSpent() {
        var pack = pack("1.5.0");
        var saved = engine(pack, null).start(new RunRequest(HIVE, POLISH, Map.of(), 7L, true)).run().snapshot();
        var pipeline = GenerationPipeline.restore(pack, POLISH, request -> {
            String path = request.slotKey().equals("lantern-slot") ? LANTERN : "generation/fallbacks/pl/ending-rest.json";
            return complete(narration(pack, path, REJECTED));
        }, new SequentialIdSource("generated"), Clock.systemUTC(), saved.state().generationArtifacts());
        var ending = pipeline.resolveScene("ending-rest-slot");
        assertThat(ending.artifact().outcome()).isEqualTo(Outcome.FALLBACK);
        assertThat(ending.execution().providerCalls()).isEqualTo(2);
        assertThat(ending.execution().failureCode()).isEqualTo("GENERATION_QUALITY_READABILITY");
        assertThat(ending.artifact().payload().toString()).doesNotContain(REJECTED);
        var next = pipeline.resolveScene("lantern-slot");
        assertThat(next.artifact().outcome()).isEqualTo(Outcome.FALLBACK);
        assertThat(next.execution().providerCalls()).isZero();
        assertThat(next.execution().failureCode()).isEqualTo("GENERATION_BUDGET_REACHED");
    }

    @Test void oldPackRestoresItsAcceptedLongCopyWithoutProvider() {
        var prior = pack("1.4.0");
        var before = engine(prior, null).start(new RunRequest(HIVE, POLISH, Map.of(), 7L, true)).run();
        assertThat(before.view().blocks()).anySatisfy(block -> assertThat(SceneWordLimits.wordCount(block.text())).isGreaterThan(20));
        var newEngine = Engine.builder(new PackRegistry(List.of(prior, pack("1.5.0"))))
                .clock(Clock.systemUTC()).ids(new SequentialIdSource("new")).random(Randoms.seeded(7))
                .textGenerator(request -> { throw new AssertionError("resume requested provider"); }).build();
        assertThat(newEngine.resume(before.snapshot()).run().view()).isEqualTo(before.view());
    }

    private static Pack pack(String version) {
        return new PackLoader(Engine.sectionValidators()).load(HIVE,
                hiveSource(version));
    }

    private static DirectoryPackSource hiveSource(String version) {
        return new DirectoryPackSource(Path.of("../reference-game-pack-versions", HIVE.value(), version));
    }

    private static Engine engine(Pack pack, TextGenerator provider) {
        var builder = Engine.builder(new PackRegistry(List.of(pack)))
                .clock(Clock.systemUTC()).ids(new SequentialIdSource("test")).random(Randoms.seeded(7));
        if (provider != null) builder.textGenerator(provider);
        return builder.build();
    }

    private static String narration(Pack pack, String path, String value) {
        return ((ObjectNode) JSON.readTree(pack.requireFile(path))).put("narration", value).toString();
    }

    private static TextGenerationResult complete(String payload) {
        return new TextGenerationResult(payload, new TextGenerationResult.TokenUsage(1, 1, 0, 0),
                "word-limit-fixture", TextGenerationResult.FinishReason.COMPLETE);
    }
}
