package com.capybee.engine.api.json;

import com.capybee.engine.api.EndReason;
import com.capybee.engine.api.Issue;
import com.capybee.engine.api.PackRef;
import com.capybee.engine.api.RunSnapshot;
import com.capybee.engine.api.RunState;
import com.capybee.engine.api.ObjectiveState;
import com.capybee.engine.generation.GenerationArtifact;
import com.capybee.engine.generation.GenerationCatalog;
import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.PackId;
import com.capybee.engine.pack.PackVersion;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SnapshotCodec {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SnapshotCodec() {}

    public static String toJson(RunSnapshot snapshot) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("snapshotVersion", snapshot.snapshotVersion());
        root.put("runId", snapshot.runId());
        ObjectNode packNode = MAPPER.createObjectNode();
        packNode.put("id", snapshot.pack().id().value());
        packNode.put("version", snapshot.pack().version().toString());
        root.set("pack", packNode);
        root.put("locale", snapshot.locale().value());
        root.put("seed", snapshot.seed());
        ObjectNode inputs = MAPPER.createObjectNode();
        snapshot.startInputs().forEach(inputs::put);
        root.set("startInputs", inputs);
        root.put("createdAt", snapshot.createdAt().toString());
        root.put("updatedAt", snapshot.updatedAt().toString());
        root.put("step", snapshot.step());
        ObjectNode stateNode = MAPPER.createObjectNode();
        stateNode.put("sceneId", snapshot.state().sceneId());
        ArrayNode visitedNode = MAPPER.createArrayNode();
        for (String visited : snapshot.state().visited()) visitedNode.add(visited);
        stateNode.set("visited", visitedNode);
        stateNode.put("ended", snapshot.state().ended());
        if (snapshot.state().endReason() != null) {
            stateNode.put("endReason", snapshot.state().endReason().name());
        }
        ObjectNode countersNode = MAPPER.createObjectNode();
        snapshot.state().counters().forEach(countersNode::put);
        stateNode.set("counters", countersNode);
        ObjectNode flagsNode = MAPPER.createObjectNode();
        snapshot.state().flags().forEach(flagsNode::put);
        stateNode.set("flags", flagsNode);
        ObjectNode setsNode = MAPPER.createObjectNode();
        snapshot.state().sets().forEach((id, values) -> {
            ArrayNode members = MAPPER.createArrayNode();
            values.stream().sorted().forEach(members::add);
            setsNode.set(id, members);
        });
        stateNode.set("sets", setsNode);
        ObjectNode objectivesNode = MAPPER.createObjectNode();
        snapshot.state().objectives().forEach((id, value) -> objectivesNode.put(id, value.name()));
        stateNode.set("objectives", objectivesNode);
        if (snapshot.state().outcome() != null) stateNode.put("outcome", snapshot.state().outcome());
        if (snapshot.state().currentPlaceId() == null) stateNode.putNull("currentPlaceId");
        else stateNode.put("currentPlaceId", snapshot.state().currentPlaceId());
        ArrayNode visitedPlacesNode = MAPPER.createArrayNode();
        for (String placeId : snapshot.state().visitedPlaces()) visitedPlacesNode.add(placeId);
        stateNode.set("visitedPlaces", visitedPlacesNode);
        ArrayNode generatedNode = MAPPER.createArrayNode();
        snapshot.state().generationArtifacts().forEach(artifact -> generatedNode.add(writeArtifact(artifact)));
        stateNode.set("generationArtifacts", generatedNode);
        root.set("state", stateNode);
        return root.toString();
    }

    public static SnapshotParse fromJson(String json) {
        List<Issue> issues = new ArrayList<>();
        try {
            JsonNode root = MAPPER.readTree(json);
            if (root == null || !root.isObject()) {
                issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID", "Snapshot root must be an object"));
                return new SnapshotParse(null, issues);
            }
            int version = parseInt(root.get("snapshotVersion"), issues, "SNAPSHOT_VERSION_UNSUPPORTED");
            if (version != 1) {
                issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_VERSION_UNSUPPORTED", "Unsupported snapshot version: " + version));
                return new SnapshotParse(null, issues);
            }
            String runId = text(root.get("runId"));
            if (runId == null || runId.isBlank()) {
                issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID", "Snapshot run id is missing"));
                return new SnapshotParse(null, issues);
            }
            JsonNode packNode = root.get("pack");
            if (packNode == null || !packNode.isObject()) {
                issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID", "Snapshot pack block is missing"));
                return new SnapshotParse(null, issues);
            }
            String packIdText = text(packNode.get("id"));
            String versionText = text(packNode.get("version"));
            if (packIdText == null || versionText == null) {
                issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID", "Snapshot pack id/version missing"));
                return new SnapshotParse(null, issues);
            }
            PackId packId = new PackId(packIdText);
            PackVersion packVersion = PackVersion.tryParse(versionText).orElse(null);
            if (packVersion == null) {
                issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID", "Snapshot pack version is invalid"));
                return new SnapshotParse(null, issues);
            }
            String localeText = text(root.get("locale"));
            if (localeText == null || !LocaleTag.isValid(localeText)) {
                issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID", "Snapshot locale is missing or invalid"));
                return new SnapshotParse(null, issues);
            }
            LocaleTag locale = new LocaleTag(localeText);
            Long seedValue = longValue(root.get("seed"));
            if (seedValue == null) {
                issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID", "Snapshot seed is missing or invalid"));
                return new SnapshotParse(null, issues);
            }
            long seed = seedValue;
            Map<String, String> inputs = new LinkedHashMap<>();
            JsonNode inputsNode = root.get("startInputs");
            if (inputsNode == null || !inputsNode.isObject()) {
                issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID", "Snapshot start inputs are missing or malformed"));
                return new SnapshotParse(null, issues);
            }
            for (var entry : inputsNode.properties()) {
                if (!entry.getValue().isString()) invalid(issues, "/startInputs/" + entry.getKey());
                else inputs.put(entry.getKey(), entry.getValue().asString());
            }
            String createdText = text(root.get("createdAt"));
            String updatedText = text(root.get("updatedAt"));
            if (createdText == null || updatedText == null) {
                issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID", "Snapshot timestamps missing"));
                return new SnapshotParse(null, issues);
            }
            Instant createdAt = Instant.parse(createdText);
            Instant updatedAt = Instant.parse(updatedText);
            Long stepValue = longValue(root.get("step"));
            if (stepValue == null || stepValue < 0) {
                issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID", "Snapshot step is missing or invalid"));
                return new SnapshotParse(null, issues);
            }
            long step = stepValue;
            JsonNode stateNode = root.get("state");
            if (stateNode == null || !stateNode.isObject()) {
                issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID", "State block is missing"));
                return new SnapshotParse(null, issues);
            }
            String sceneId = text(stateNode.get("sceneId"));
            if (sceneId == null || sceneId.isBlank()) {
                issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID", "Snapshot current scene is missing"));
                return new SnapshotParse(null, issues);
            }
            List<String> visited = new ArrayList<>();
            JsonNode visitedNode = stateNode.get("visited");
            if (visitedNode == null || !visitedNode.isArray()) {
                issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID", "Snapshot visited list is missing or malformed"));
                return new SnapshotParse(null, issues);
            }
            for (int index = 0; index < visitedNode.size(); index++) {
                JsonNode item = visitedNode.get(index);
                if (!item.isString()) invalid(issues, "/state/visited/" + index);
                else visited.add(item.asString());
            }
            JsonNode endedNode = stateNode.get("ended");
            if (endedNode == null || !endedNode.isBoolean()) {
                issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID", "Snapshot ended flag is missing or malformed"));
                return new SnapshotParse(null, issues);
            }
            boolean ended = endedNode.booleanValue();
            String endReasonText = text(stateNode.get("endReason"));
            EndReason endReason = null;
            if (stateNode.has("endReason") && !stateNode.get("endReason").isNull() && endReasonText == null) {
                invalid(issues, "/state/endReason");
            }
            if (endReasonText != null) {
                try {
                    endReason = EndReason.valueOf(endReasonText);
                } catch (IllegalArgumentException exception) {
                    invalid(issues, "/state/endReason");
                }
            }
            Map<String, Integer> counters = readIntegerMap(stateNode.get("counters"), issues, "/state/counters");
            Map<String, Boolean> flags = readBooleanMap(stateNode.get("flags"), issues, "/state/flags");
            Map<String, List<String>> sets = readStringLists(stateNode.get("sets"), issues, "/state/sets");
            Map<String, ObjectiveState> objectives = readObjectives(stateNode.get("objectives"), issues);
            String outcome = text(stateNode.get("outcome"));
            if (stateNode.has("outcome") && outcome == null && !stateNode.get("outcome").isNull()) {
                issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID", "Snapshot outcome is malformed"));
            }
            String currentPlaceId = null;
            if (stateNode.has("currentPlaceId") && !stateNode.get("currentPlaceId").isNull()) {
                currentPlaceId = text(stateNode.get("currentPlaceId"));
                if (currentPlaceId == null) invalid(issues, "/state/currentPlaceId");
            }
            List<String> visitedPlaces = new ArrayList<>();
            JsonNode visitedPlacesNode = stateNode.get("visitedPlaces");
            if (visitedPlacesNode != null) {
                if (!visitedPlacesNode.isArray()) invalid(issues, "/state/visitedPlaces");
                else {
                    for (int index = 0; index < visitedPlacesNode.size(); index++) {
                        JsonNode item = visitedPlacesNode.get(index);
                        if (!item.isString()) invalid(issues, "/state/visitedPlaces/" + index);
                        else visitedPlaces.add(item.asString());
                    }
                }
            }
            List<GenerationArtifact> generationArtifacts = readGenerationArtifacts(
                    stateNode.get("generationArtifacts"), issues);
            if (issues.stream().anyMatch(issue -> issue.severity() == Issue.Severity.ERROR)) {
                return new SnapshotParse(null, issues);
            }
            RunState state = new RunState(sceneId, visited, ended, endReason, counters, flags, sets, objectives,
                    outcome, currentPlaceId, visitedPlaces, generationArtifacts);
            RunSnapshot snapshot = new RunSnapshot(version, runId, new PackRef(packId, packVersion), locale, seed, inputs, createdAt, updatedAt, step, state);
            return new SnapshotParse(snapshot, issues);
        } catch (Exception exception) {
            issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID", "Snapshot JSON is malformed: " + exception.getMessage()));
            return new SnapshotParse(null, issues);
        }
    }

    private static int parseInt(JsonNode node, List<Issue> issues, String code) {
        if (node == null || !node.isIntegralNumber()) {
            issues.add(new Issue(Issue.Severity.ERROR, code, "Expected numeric value"));
            return 0;
        }
        if (!node.canConvertToInt()) {
            issues.add(new Issue(Issue.Severity.ERROR, code, "Expected signed 32-bit numeric value"));
            return 0;
        }
        return node.asInt();
    }

    private static Long longValue(JsonNode node) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong()) return null;
        return node.longValue();
    }

    private static Map<String, Integer> readIntegerMap(JsonNode node, List<Issue> issues, String pointer) {
        Map<String, Integer> values = new LinkedHashMap<>();
        if (node == null) return values;
        if (!node.isObject()) {
            invalid(issues, pointer);
            return values;
        }
        node.properties().forEach(entry -> {
            if (!entry.getValue().isIntegralNumber() || !entry.getValue().canConvertToInt()) invalid(issues, pointer + "/" + entry.getKey());
            else values.put(entry.getKey(), entry.getValue().intValue());
        });
        return values;
    }

    private static Map<String, Boolean> readBooleanMap(JsonNode node, List<Issue> issues, String pointer) {
        Map<String, Boolean> values = new LinkedHashMap<>();
        if (node == null) return values;
        if (!node.isObject()) {
            invalid(issues, pointer);
            return values;
        }
        node.properties().forEach(entry -> {
            if (!entry.getValue().isBoolean()) invalid(issues, pointer + "/" + entry.getKey());
            else values.put(entry.getKey(), entry.getValue().booleanValue());
        });
        return values;
    }

    private static Map<String, List<String>> readStringLists(JsonNode node, List<Issue> issues, String pointer) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        if (node == null) return values;
        if (!node.isObject()) {
            invalid(issues, pointer);
            return values;
        }
        node.properties().forEach(entry -> {
            JsonNode members = entry.getValue();
            if (!members.isArray()) {
                invalid(issues, pointer + "/" + entry.getKey());
                return;
            }
            List<String> parsed = new ArrayList<>();
            for (int index = 0; index < members.size(); index++) {
                JsonNode member = members.get(index);
                if (!member.isString()) invalid(issues, pointer + "/" + entry.getKey() + "/" + index);
                else parsed.add(member.asString());
            }
            values.put(entry.getKey(), parsed);
        });
        return values;
    }

    private static Map<String, ObjectiveState> readObjectives(JsonNode node, List<Issue> issues) {
        Map<String, ObjectiveState> values = new LinkedHashMap<>();
        if (node == null) return values;
        if (!node.isObject()) {
            invalid(issues, "/state/objectives");
            return values;
        }
        node.properties().forEach(entry -> {
            if (!entry.getValue().isString()) {
                invalid(issues, "/state/objectives/" + entry.getKey());
                return;
            }
            try {
                values.put(entry.getKey(), ObjectiveState.valueOf(entry.getValue().asString()));
            } catch (IllegalArgumentException exception) {
                invalid(issues, "/state/objectives/" + entry.getKey());
            }
        });
        return values;
    }

    private static void invalid(List<Issue> issues, String pointer) {
        issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID", "Malformed snapshot state at " + pointer));
    }

    private static ObjectNode writeArtifact(GenerationArtifact artifact) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("artifactSchemaVersion", artifact.artifactSchemaVersion());
        node.put("packId", artifact.packId().value());
        node.put("packVersion", artifact.packVersion().toString());
        node.put("locale", artifact.locale().value());
        node.put("stage", artifact.stage().name());
        node.put("templateId", artifact.templateId());
        node.put("artifactId", artifact.artifactId());
        node.put("outcome", artifact.outcome().name());
        ObjectNode payload = MAPPER.createObjectNode();
        if (artifact.payload() instanceof GenerationArtifact.ThemePayload value) {
            payload.put("title", value.title()); payload.put("premise", value.premise());
            payload.set("toneTags", stringArray(value.toneTags()));
        } else if (artifact.payload() instanceof GenerationArtifact.ScenarioPayload value) {
            payload.put("title", value.title()); payload.put("premise", value.premise());
            payload.put("setting", value.setting()); payload.set("beats", stringArray(value.beats()));
        } else if (artifact.payload() instanceof GenerationArtifact.CastPayload value) {
            ArrayNode members = MAPPER.createArrayNode();
            value.characters().forEach(member -> {
                ObjectNode item = MAPPER.createObjectNode();
                item.put("slotId", member.slotId()); item.put("actorId", member.actorId());
                item.put("name", member.name()); item.put("description", member.description());
                members.add(item);
            });
            payload.set("characters", members);
        } else if (artifact.payload() instanceof GenerationArtifact.QuestPayload value) {
            ArrayNode objectives = MAPPER.createArrayNode();
            value.objectives().forEach(quest -> {
                ObjectNode item = MAPPER.createObjectNode();
                item.put("objectiveId", quest.objectiveId()); item.put("title", quest.title());
                item.put("description", quest.description()); objectives.add(item);
            });
            payload.set("objectives", objectives);
        } else if (artifact.payload() instanceof GenerationArtifact.ScenePayload value) {
            payload.put("sceneId", value.sceneId()); payload.put("title", value.title());
            payload.put("narration", value.narration());
            ArrayNode dialogue = MAPPER.createArrayNode();
            value.dialogue().forEach(line -> {
                ObjectNode item = MAPPER.createObjectNode();
                item.put("actorId", line.actorId()); item.put("text", line.text()); dialogue.add(item);
            });
            payload.set("dialogue", dialogue);
            ArrayNode choices = MAPPER.createArrayNode();
            value.choices().forEach(choice -> {
                ObjectNode item = MAPPER.createObjectNode(); item.put("id", choice.id()); item.put("label", choice.label()); choices.add(item);
            });
            payload.set("choices", choices);
        }
        node.set("payload", payload);
        return node;
    }

    private static ArrayNode stringArray(List<String> values) {
        ArrayNode array = MAPPER.createArrayNode();
        values.forEach(array::add);
        return array;
    }

    private static List<GenerationArtifact> readGenerationArtifacts(JsonNode node, List<Issue> issues) {
        List<GenerationArtifact> artifacts = new ArrayList<>();
        if (node == null) return artifacts;
        if (!node.isArray() || node.size() > 256) {
            invalid(issues, "/state/generationArtifacts");
            return artifacts;
        }
        for (int index = 0; index < node.size(); index++) {
            String pointer = "/state/generationArtifacts/" + index;
            JsonNode item = node.get(index);
            if (!exactFields(item, "artifactSchemaVersion", "packId", "packVersion", "locale", "stage",
                    "templateId", "artifactId", "outcome", "payload")) {
                invalid(issues, pointer);
                continue;
            }
            int schemaVersion = intValue(item.get("artifactSchemaVersion"));
            String packId = text(item.get("packId"));
            String packVersionText = text(item.get("packVersion"));
            String locale = text(item.get("locale"));
            String stageText = text(item.get("stage"));
            String templateId = text(item.get("templateId"));
            String artifactId = text(item.get("artifactId"));
            String outcomeText = text(item.get("outcome"));
            GenerationCatalog.Stage stage = enumValue(GenerationCatalog.Stage.class, stageText);
            GenerationArtifact.Outcome outcome = enumValue(GenerationArtifact.Outcome.class, outcomeText);
            PackVersion packVersion = PackVersion.tryParse(packVersionText).orElse(null);
            GenerationArtifact.Payload payload = readPayload(stage, item.get("payload"), issues, pointer + "/payload");
            if (schemaVersion != GenerationArtifact.SUPPORTED_SCHEMA_VERSION || packId == null || packVersion == null
                    || locale == null || !LocaleTag.isValid(locale) || stage == null || outcome == null
                    || templateId == null || artifactId == null || artifactId.isBlank() || payload == null) {
                invalid(issues, pointer);
                continue;
            }
            try {
                artifacts.add(new GenerationArtifact(schemaVersion, new PackId(packId), packVersion,
                        new LocaleTag(locale), stage, templateId, artifactId, outcome, payload));
            } catch (RuntimeException exception) {
                invalid(issues, pointer);
            }
        }
        return artifacts;
    }

    private static GenerationArtifact.Payload readPayload(GenerationCatalog.Stage stage, JsonNode node,
                                                           List<Issue> issues, String pointer) {
        if (stage == null || node == null || !node.isObject()) return null;
        try {
            return switch (stage) {
                case THEME -> {
                    if (!exactFields(node, "title", "premise", "toneTags")) yield null;
                    yield new GenerationArtifact.ThemePayload(requiredText(node, "title"), requiredText(node, "premise"),
                            textArray(node.get("toneTags")));
                }
                case SCENARIO -> {
                    if (!exactFields(node, "title", "premise", "setting", "beats")) yield null;
                    yield new GenerationArtifact.ScenarioPayload(requiredText(node, "title"), requiredText(node, "premise"),
                            requiredText(node, "setting"), textArray(node.get("beats")));
                }
                case CAST -> {
                    if (!exactFields(node, "characters") || !node.get("characters").isArray()) yield null;
                    List<GenerationArtifact.CastMember> members = new ArrayList<>();
                    for (JsonNode member : node.get("characters")) {
                        if (!exactFields(member, "slotId", "actorId", "name", "description")) yield null;
                        members.add(new GenerationArtifact.CastMember(requiredText(member, "slotId"),
                            requiredText(member, "actorId"),
                                requiredText(member, "name"), requiredText(member, "description")));
                    }
                    yield new GenerationArtifact.CastPayload(members);
                }
                case QUEST -> {
                    if (!exactFields(node, "objectives") || !node.get("objectives").isArray()) yield null;
                    List<GenerationArtifact.Quest> quests = new ArrayList<>();
                    for (JsonNode quest : node.get("objectives")) {
                        if (!exactFields(quest, "objectiveId", "title", "description")) yield null;
                        quests.add(new GenerationArtifact.Quest(requiredText(quest, "objectiveId"),
                                requiredText(quest, "title"), requiredText(quest, "description")));
                    }
                    yield new GenerationArtifact.QuestPayload(quests);
                }
                case SCENE -> {
                    if (!exactFields(node, "sceneId", "title", "narration", "dialogue", "choices")
                            || !node.get("dialogue").isArray() || !node.get("choices").isArray()) yield null;
                    List<GenerationArtifact.Dialogue> dialogue = new ArrayList<>();
                    for (JsonNode line : node.get("dialogue")) {
                        if (!exactFields(line, "actorId", "text")) yield null;
                        dialogue.add(new GenerationArtifact.Dialogue(requiredText(line, "actorId"), requiredText(line, "text")));
                    }
                    List<GenerationArtifact.ChoiceLabel> choices = new ArrayList<>();
                    for (JsonNode choice : node.get("choices")) {
                        if (!exactFields(choice, "id", "label")) yield null;
                        choices.add(new GenerationArtifact.ChoiceLabel(requiredText(choice, "id"), requiredText(choice, "label")));
                    }
                    yield new GenerationArtifact.ScenePayload(requiredText(node, "sceneId"), requiredText(node, "title"),
                            requiredText(node, "narration"), dialogue, choices);
                }
            };
        } catch (RuntimeException exception) {
            invalid(issues, pointer);
            return null;
        }
    }

    private static boolean exactFields(JsonNode node, String... expectedFields) {
        if (node == null || !node.isObject()) return false;
        java.util.Set<String> expected = java.util.Set.of(expectedFields);
        java.util.Set<String> actual = new java.util.HashSet<>();
        node.propertyNames().forEach(actual::add);
        return actual.equals(expected);
    }

    private static String requiredText(JsonNode node, String field) {
        String value = text(node.get(field));
        if (value == null) throw new IllegalArgumentException("SNAPSHOT_INVALID");
        return value;
    }

    private static List<String> textArray(JsonNode node) {
        if (node == null || !node.isArray()) throw new IllegalArgumentException("SNAPSHOT_INVALID");
        List<String> values = new ArrayList<>();
        for (JsonNode value : node) values.add(requiredTextValue(value));
        return values;
    }

    private static String requiredTextValue(JsonNode node) {
        if (node == null || !node.isString()) throw new IllegalArgumentException("SNAPSHOT_INVALID");
        return node.asString();
    }

    private static int intValue(JsonNode node) {
        return node != null && node.isIntegralNumber() && node.canConvertToInt() ? node.intValue() : -1;
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        if (value == null) return null;
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException exception) { return null; }
    }

    private static String text(JsonNode node) {
        return node != null && node.isString() ? node.asString() : null;
    }

    public record SnapshotParse(RunSnapshot snapshot, List<Issue> issues) {}
}
