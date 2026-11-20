package com.capybee.server.game;

import com.capybee.engine.adapter.Randoms;
import com.capybee.engine.adapter.UuidIdSource;
import com.capybee.engine.api.Command;
import com.capybee.engine.api.Engine;
import com.capybee.engine.api.RunRequest;
import com.capybee.engine.generation.GenerationArtifact;
import com.capybee.engine.generation.GenerationCatalog;
import com.capybee.engine.generation.GenerationQualityEvaluator;
import com.capybee.engine.generation.json.GenerationQualityProfileParser;
import com.capybee.engine.pack.*;
import com.capybee.engine.pack.json.PackLoader;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class PackTextBudgetTest {
    private static final PackId ID = new PackId("capybee-new-hive");
    private static final LocaleTag POLISH = new LocaleTag("pl");
    private static final String STORY = "scenes/pl/story.json";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Set<String> RETIRED = Set.of("visit-lantern", "meet-mela", "meet-borys", "meet-luna", "visit-workshop");

    @Test void shortPackPreservesMechanicsAndReviewedEndings() {
        var previous = load("1.4.0");
        var current = load("1.5.0");
        var priorStory = JSON.readTree(previous.requireFile(STORY));
        var story = JSON.readTree(current.requireFile(STORY));
        var priorScenes = priorStory.path("scenes");
        assertThat(story.path("scenes").size()).isEqualTo(priorScenes.size());
        String endingMarker = "\"id\": \"ending-new\"";
        assertThat(current.requireFile(STORY).substring(current.requireFile(STORY).indexOf(endingMarker)))
                .isEqualTo(previous.requireFile(STORY).substring(previous.requireFile(STORY).indexOf(endingMarker)));

        var profile = GenerationQualityProfileParser.parse(current, POLISH, "generation/quality/pl.json");
        assertThat(profile.profileId()).isEqualTo("hive-family-pl-v1");
        for (int i = 0; i < priorScenes.size(); i++) {
            ObjectNode expected = (ObjectNode) priorScenes.get(i).deepCopy();
            var actual = story.path("scenes").get(i);
            String id = expected.path("id").asString();
            if (expected.path("end").asBoolean()) {
                assertThat(actual).as(id).isEqualTo(expected);
                String path = "generation/fallbacks/pl/" + id + ".json";
                assertThat(current.requireFile(path)).isEqualTo(previous.requireFile(path));
                continue;
            }
            var narration = actual.path("blocks");
            assertThat(narration.size()).as(id).isEqualTo(1);
            assertThat(narration.get(0).path("kind").asString()).as(id).isEqualTo("NARRATION");
            String text = narration.get(0).path("text").asString();
            assertThat(text.lines().count()).as(id).isBetween(1L, 2L);
            assertThat(words(text)).as(id).isLessThanOrEqualTo(32);
            for (String sentence : text.split("[.!?]+")) {
                assertThat(words(sentence)).as(sentence).isLessThanOrEqualTo(14);
            }
            ((ObjectNode) expected.path("blocks").get(0)).put("text", text);
            var labels = new ArrayList<GenerationArtifact.ChoiceLabel>();
            var expectedOptions = expected.withArray("options");
            if (id.equals("board")) {
                for (int j = expectedOptions.size()-1; j >= 0; j--) {
                    if (RETIRED.contains(expectedOptions.get(j).path("id").asString())) expectedOptions.remove(j);
                }
            }
            for (int j = 0; j < expectedOptions.size(); j++) {
                var option = actual.path("options").get(j);
                String label = option.path("label").asString();
                String optionId = option.path("id").asString();
                assertThat(words(label)).as(optionId).isBetween(1L, 5L);
                assertThat(label.codePointCount(0, label.length())).as(optionId).isLessThanOrEqualTo(36);
                if (optionId.equals("rest-now")) assertThat(label).isEqualTo("Na dziś wybieram odpoczynek");
                ((ObjectNode) expectedOptions.get(j)).put("label", label);
                labels.add(new GenerationArtifact.ChoiceLabel(optionId, label));
            }
            if (!actual.has("options") && expectedOptions.isEmpty()) expected.remove("options");
            assertThat(actual).as(id).isEqualTo(expected);
            if (!labels.isEmpty()) assertThat(labels).anyMatch(label -> label.id().equals("rest-now"));

            var fallback = JSON.readTree(current.requireFile("generation/fallbacks/pl/" + id + ".json"));
            assertThat(fallback.path("narration").asString()).as(id).isEqualTo(text);
            assertThat(fallback.path("choices")).as(id).isEqualTo((JsonNode) JSON.valueToTree(labels));
            var payload = new GenerationArtifact.PlannedScenePayload(id, fallback.path("title").asString(), text,
                    List.of(), labels, List.of());
            var context = new GenerationQualityEvaluator.QualityContext(List.copyOf(profile.toneProfiles().keySet()),
                    List.of(), Set.of(), false);
            assertThat(new GenerationQualityEvaluator().evaluate(GenerationCatalog.Stage.SCENE, payload, context, profile).findings())
                    .as(id).isEmpty();
        }
        var oldRoot = (ObjectNode) priorStory;
        var newRoot = (ObjectNode) story;
        oldRoot.remove("scenes");
        newRoot.remove("scenes");
        assertThat(newRoot).isEqualTo(oldRoot);
        for (String path : previous.files().keySet()) {
            if (!Set.of("pack.json", STORY, "strings/pl.json", "generation/catalog.json").contains(path)
                    && !path.startsWith("generation/fallbacks/pl/")) {
                assertThat(current.requireFile(path)).as(path).isEqualTo(previous.requireFile(path));
            }
        }
        var currentStrings = JSON.readTree(current.requireFile("strings/pl.json"));
        var expectedStrings = (ObjectNode) JSON.readTree(previous.requireFile("strings/pl.json"));
        expectedStrings.put("navigation.choose-or-pause", "Dokończ tę chwilę albo zrób przerwę.");
        assertThat(currentStrings).isEqualTo(expectedStrings);
        currentStrings.properties().forEach(entry -> {
            String key = entry.getKey();
            if (key.startsWith("description.") || key.startsWith("hint.") || key.startsWith("navigation.")) {
                assertThat(words(entry.getValue().asString())).as(key).isLessThanOrEqualTo(13);
            }
        });
        assertThat(current.manifest().contentPolicy().forbiddenTags())
                .containsExactlyInAnyOrder("personal-data", "pressure", "human-replacement");
    }

    @Test void packagedUpgradeRetainsExactBytesAndResumesOldHub() throws Exception {
        var previous = load("1.4.0");
        var registry = new GameSessionConfiguration().gamePackRegistry(ID.value());
        var retained = registry.find(ID, new PackVersion(1,4,0)).orElseThrow();
        assertThat(retained.files()).isEqualTo(previous.files());
        try (var files = Files.walk(directory("1.4.0"))) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String resource = "game-packs/" + ID.value() + "/retained/1.4.0/"
                        + directory("1.4.0").relativize(file).toString().replace('\\', '/');
                try (var packaged = getClass().getClassLoader().getResourceAsStream(resource)) {
                    assertThat(packaged).as(resource).isNotNull();
                    assertThat(packaged.readAllBytes()).as(resource).isEqualTo(Files.readAllBytes(file));
                }
            }
        }
        var request = new RunRequest(ID, POLISH, Map.of(), 7L);
        var oldRun = engine(new PackRegistry(List.of(previous))).start(request).run();
        assertThat(oldRun.apply(new Command.Advance()).accepted()).isTrue();
        var engine = engine(registry);
        var resumed = engine.resume(oldRun.snapshot());
        assertThat(resumed.ok()).isTrue();
        assertThat(resumed.run().view()).isEqualTo(oldRun.view());
        assertThat(resumed.run().view().options()).hasSize(6);
        var newRun = engine.start(request).run();
        assertThat(newRun.apply(new Command.Advance()).accepted()).isTrue();
        assertThat(newRun.view().options()).hasSize(1);
    }

    private static Engine engine(PackRegistry registry) {
        return Engine.builder(registry).clock(Clock.systemUTC()).random(Randoms.seeded(7))
                .ids(new UuidIdSource()).build();
    }

    private static long words(String text) { return WORD.matcher(text).results().count(); }
    private static Path directory(String version) {
        return Path.of("../engine/reference-game-pack-versions", ID.value(), version);
    }
    private static Pack load(String version) {
        return new PackLoader(Engine.sectionValidators()).load(ID, new DirectoryPackSource(directory(version)));
    }
}
