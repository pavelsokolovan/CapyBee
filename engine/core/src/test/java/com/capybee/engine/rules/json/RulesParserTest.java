package com.capybee.engine.rules.json;

import com.capybee.engine.pack.DirectoryPackSource;
import com.capybee.engine.pack.Pack;
import com.capybee.engine.pack.PackId;
import com.capybee.engine.pack.PackSource;
import com.capybee.engine.pack.PackValidationContext;
import com.capybee.engine.pack.json.PackLoader;
import com.capybee.engine.rules.Condition;
import com.capybee.engine.rules.Effect;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RulesParserTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final PackId PACK_ID = new PackId("fixture-text-dungeon");

    private Pack pack() {
        return new PackLoader(com.capybee.engine.api.Engine.sectionValidators()).load(PACK_ID,
                new DirectoryPackSource(Path.of("..", "fixtures", "game-packs", "fixture-text-dungeon")));
    }

    @Test
    void parsesEveryConditionKindAndComposite() throws Exception {
        Pack pack = pack();
        List<String> conditions = List.of(
                "{\"type\":\"COUNTER_AT_LEAST\",\"counter\":\"health\",\"value\":3}",
                "{\"type\":\"COUNTER_AT_MOST\",\"counter\":\"health\",\"value\":3}",
                "{\"type\":\"COUNTER_EQUALS\",\"counter\":\"health\",\"value\":3}",
                "{\"type\":\"FLAG_IS\",\"flag\":\"has-key\",\"value\":true}",
                "{\"type\":\"SET_CONTAINS\",\"set\":\"inventory\",\"value\":\"key\"}",
                "{\"type\":\"SET_SIZE_AT_LEAST\",\"set\":\"inventory\",\"size\":1}",
                "{\"type\":\"VISITED_SCENE\",\"scene\":\"exit\"}",
                "{\"type\":\"STEP_AT_LEAST\",\"step\":3}",
                "{\"type\":\"ALL\",\"conditions\":[{\"type\":\"FLAG_IS\",\"flag\":\"has-key\",\"value\":true}]}",
                "{\"type\":\"ANY\",\"conditions\":[{\"type\":\"STEP_AT_LEAST\",\"step\":1}]}",
                "{\"type\":\"NOT\",\"condition\":{\"type\":\"FLAG_IS\",\"flag\":\"has-key\",\"value\":false}}"
        );

        for (String json : conditions) {
            PackValidationContext context = new PackValidationContext();
            Condition condition = RulesParser.parseCondition(MAPPER.readTree(json), pack, context, "test.json", "");
            assertThat(condition).isNotNull();
            assertThat(context.issues()).isEmpty();
        }
    }

    @Test
    void rejectsUnsupportedMalformedAndOverLimitConditions() throws Exception {
        Pack pack = pack();
        for (String json : List.of("{\"type\":\"RANDOM\"}",
                "{\"type\":\"FLAG_IS\",\"flag\":\"has-key\",\"value\":\"true\"}",
                "{\"type\":\"SET_SIZE_AT_LEAST\",\"set\":\"inventory\",\"size\":-1}",
                "{\"type\":\"ALL\",\"conditions\":[]}")) {
            PackValidationContext context = new PackValidationContext();
            assertThat(RulesParser.parseCondition(MAPPER.readTree(json), pack, context, "test.json", "" )).isNull();
            assertThat(context.issues()).anyMatch(issue -> issue.code().equals("CONDITION_INVALID"));
        }

        String nested = "{\"type\":\"FLAG_IS\",\"flag\":\"has-key\",\"value\":true}";
        for (int depth = 0; depth < 4; depth++) nested = "{\"type\":\"NOT\",\"condition\":" + nested + "}";
        PackValidationContext depthContext = new PackValidationContext();
        assertThat(RulesParser.parseCondition(MAPPER.readTree(nested), pack, depthContext, "test.json", "")).isNull();
        assertThat(depthContext.issues()).anyMatch(issue -> issue.code().equals("CONDITION_INVALID"));

        String children = String.join(",", java.util.Collections.nCopies(17,
                "{\"type\":\"STEP_AT_LEAST\",\"step\":0}"));
        PackValidationContext listContext = new PackValidationContext();
        assertThat(RulesParser.parseCondition(MAPPER.readTree("{\"type\":\"ALL\",\"conditions\":[" + children + "]}"),
                pack, listContext, "test.json", "")).isNull();
        assertThat(listContext.issues()).anyMatch(issue -> issue.code().equals("CONDITION_INVALID"));
    }

    @Test
    void parsesEveryEffectAndTypedEventPayload() throws Exception {
        Pack pack = pack();
        String json = "["
                + "{\"type\":\"COUNTER_ADD\",\"counter\":\"health\",\"delta\":-2},"
                + "{\"type\":\"COUNTER_SET\",\"counter\":\"health\",\"value\":5},"
                + "{\"type\":\"FLAG_SET\",\"flag\":\"has-key\",\"value\":true},"
                + "{\"type\":\"SET_ADD\",\"set\":\"inventory\",\"value\":\"key\"},"
                + "{\"type\":\"SET_REMOVE\",\"set\":\"inventory\",\"value\":\"key\"},"
                + "{\"type\":\"EMIT_EVENT\",\"event\":\"run.completed\",\"payload\":{\"outcome\":\"escaped\",\"health\":{\"counter\":\"health\"}}},"
                + "{\"type\":\"END_RUN\"},"
                + "{\"type\":\"END_RUN\",\"outcome\":\"escaped\"}]";
        PackValidationContext context = new PackValidationContext();
        List<Effect> effects = RulesParser.parseEffects(MAPPER.readTree(json), pack, context, "test.json", "/effects", false);

        assertThat(effects).hasSize(8);
        assertThat(effects.get(5)).isInstanceOf(Effect.EmitEvent.class);
        Effect.EmitEvent event = (Effect.EmitEvent) effects.get(5);
        assertThat(event.payload().get("health")).isEqualTo(new Effect.PayloadValue.CounterRef("health"));
        assertThat(context.issues()).isEmpty();
    }

    @Test
    void reportsMalformedEffectsAndEventPayloadsWithSpecificCodes() throws Exception {
        Pack pack = pack();
        String json = "["
                + "{\"type\":\"UNKNOWN\"},"
                + "{\"type\":\"COUNTER_ADD\",\"counter\":\"health\",\"delta\":0},"
                + "{\"type\":\"EMIT_EVENT\",\"event\":\"missing.event\",\"payload\":{}},"
                + "{\"type\":\"EMIT_EVENT\",\"event\":\"run.completed\",\"payload\":{\"outcome\":\"escaped\"}}]";
        PackValidationContext context = new PackValidationContext();
        List<Effect> effects = RulesParser.parseEffects(MAPPER.readTree(json), pack, context, "test.json", "/effects", false);

        assertThat(effects).isEmpty();
        assertThat(context.issues()).extracting(issue -> issue.code())
                .contains("EFFECT_INVALID", "EVENT_UNDECLARED", "EVENT_PAYLOAD_INVALID");
        PackValidationContext oversized = new PackValidationContext();
        ArrayNode manyEffects = MAPPER.createArrayNode();
        for (int index = 0; index < 33; index++) manyEffects.add(MAPPER.createObjectNode());
        assertThat(RulesParser.parseEffects(manyEffects, pack, oversized, "test.json", "/effects", false)).isEmpty();
        assertThat(oversized.issues()).anyMatch(issue -> issue.code().equals("EFFECT_INVALID"));
    }

    @Test
    void validatesRuleDefinitionsAndCrossSectionEffectPolicies() {
        Pack base = pack();
        Map<String, String> files = new LinkedHashMap<>(base.files());
        files.put("rules.json", files.get("rules.json").replace("\"maxDelta\": 3", "\"maxDelta\": 0"));
        Pack invalidRules = new Pack(base.manifest(), files);
        PackValidationContext rulesContext = new PackValidationContext();
        new RulesSectionValidator().validate(invalidRules, rulesContext);
        assertThat(rulesContext.issues()).anyMatch(issue -> issue.code().equals("RULES_FIELD_INVALID"));

        files = new LinkedHashMap<>(base.files());
        files.put("scenes/en/dungeon.json", files.get("scenes/en/dungeon.json").replace("\"delta\": -2", "\"delta\": -4"));
        Pack invalidEffect = new Pack(base.manifest(), files);
        PackValidationContext effectContext = new PackValidationContext();
        new RuleReferenceValidator().validate(invalidEffect, effectContext);
        assertThat(effectContext.issues()).anyMatch(issue -> issue.code().equals("EFFECT_INVALID"));
    }

    @Test
    void warnsWhenManifestEventIsNeverEmitted() {
        Pack base = pack();
        Map<String, String> files = new LinkedHashMap<>(base.files());
        JsonNode root = MAPPER.readTree(files.get("rules.json"));
        ArrayNode objectives = (ArrayNode) root.get("objectives");
        ((ObjectNode) objectives.get(0)).set("onComplete", MAPPER.createArrayNode());
        files.put("rules.json", root.toString());
        Pack noEvent = new Pack(base.manifest(), files);
        PackValidationContext context = new PackValidationContext();
        new RuleReferenceValidator().validate(noEvent, context);
        assertThat(context.issues()).anyMatch(issue -> issue.code().equals("EVENT_NEVER_EMITTED"));
    }

    @Test
    void malformedBlockKindProducesValidationIssueInsteadOfThrowing() throws Exception {
        Pack base = pack();
        JsonNode root = MAPPER.readTree(base.requireFile("scenes/en/dungeon.json"));
        ArrayNode scenes = (ArrayNode) root.get("scenes");
        ObjectNode firstBlock = (ObjectNode) scenes.get(0).get("blocks").get(0);
        firstBlock.put("kind", "NOT_A_BLOCK_KIND");
        String changedScenes = root.toString();
        var result = new PackLoader(com.capybee.engine.api.Engine.sectionValidators())
            .tryLoad(PACK_ID, sourceWithScenes(changedScenes));

        assertThat(result.ok()).isFalse();
        assertThat(result.issues()).anyMatch(issue -> issue.code().equals("SCENE_FIELD_INVALID"));
    }

    @Test
    void endRunEffectAllowsPackWithoutAnEndScene() throws Exception {
        Pack base = pack();
        JsonNode root = MAPPER.readTree(base.requireFile("scenes/en/dungeon.json"));
        ObjectNode cell = scene((ObjectNode) root, "cell");
        cell.set("onEnter", MAPPER.createArrayNode().add(
            (ObjectNode) MAPPER.readTree("{\"type\":\"END_RUN\",\"outcome\":\"escaped\"}")));
        ObjectNode exit = scene((ObjectNode) root, "exit");
        exit.put("end", false);
        exit.put("next", "cell");
        exit.remove("outcome");
        String changedScenes = root.toString();
        var result = new PackLoader(com.capybee.engine.api.Engine.sectionValidators())
            .tryLoad(PACK_ID, sourceWithScenes(changedScenes));

        assertThat(result.ok()).isTrue();
        assertThat(result.issues()).noneMatch(issue -> issue.code().equals("SCENE_NO_END"));
    }

    private static ObjectNode scene(ObjectNode root, String id) {
        ArrayNode scenes = (ArrayNode) root.get("scenes");
        for (int index = 0; index < scenes.size(); index++) {
            ObjectNode scene = (ObjectNode) scenes.get(index);
            if (scene.get("id").asString().equals(id)) return scene;
        }
        throw new IllegalArgumentException("Missing test scene: " + id);
    }

    private static PackSource sourceWithScenes(String scenesJson) {
        DirectoryPackSource directory = new DirectoryPackSource(Path.of("..", "fixtures", "game-packs", "fixture-text-dungeon"));
        return new PackSource() {
            @Override
            public Optional<String> read(String relativePath) {
                return relativePath.equals("scenes/en/dungeon.json") ? Optional.of(scenesJson) : directory.read(relativePath);
            }

            @Override
            public String describe() {
                return directory.describe();
            }
        };
    }
}