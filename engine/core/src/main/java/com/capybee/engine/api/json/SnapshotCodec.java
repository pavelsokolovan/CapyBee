package com.capybee.engine.api.json;

import com.capybee.engine.api.EndReason;
import com.capybee.engine.api.Issue;
import com.capybee.engine.api.PackRef;
import com.capybee.engine.api.RunSnapshot;
import com.capybee.engine.api.RunState;
import com.capybee.engine.api.ObjectiveState;
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
            if (issues.stream().anyMatch(issue -> issue.severity() == Issue.Severity.ERROR)) {
                return new SnapshotParse(null, issues);
            }
            RunState state = new RunState(sceneId, visited, ended, endReason, counters, flags, sets, objectives, outcome);
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

    private static String text(JsonNode node) {
        return node != null && node.isString() ? node.asString() : null;
    }

    public record SnapshotParse(RunSnapshot snapshot, List<Issue> issues) {}
}
