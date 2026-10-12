package com.capybee.engine.api.json;

import com.capybee.engine.api.EndReason;
import com.capybee.engine.api.Issue;
import com.capybee.engine.api.PackRef;
import com.capybee.engine.api.RunSnapshot;
import com.capybee.engine.api.RunState;
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
        root.set("state", stateNode);
        return root.toString();
    }

    public static SnapshotParse fromJson(String json) {
        List<Issue> issues = new ArrayList<>();
        try {
            JsonNode root = MAPPER.readTree(json);
            if (!root.isObject()) {
                issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID", "Snapshot root must be an object"));
                return new SnapshotParse(null, issues);
            }
            int version = parseInt(root.get("snapshotVersion"), issues, "SNAPSHOT_VERSION_UNSUPPORTED");
            if (version != 1) {
                if (version == 0 || root.has("snapshotVersion")) {
                    issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_VERSION_UNSUPPORTED", "Unsupported snapshot version: " + version));
                }
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
            LocaleTag locale = localeText == null ? null : new LocaleTag(localeText);
            long seed = root.get("seed") == null ? 0L : root.get("seed").asLong();
            Map<String, String> inputs = new LinkedHashMap<>();
            JsonNode inputsNode = root.get("startInputs");
            if (inputsNode != null && inputsNode.isObject()) {
                inputsNode.properties().forEach(entry -> inputs.put(entry.getKey(), entry.getValue().asText()));
            }
            String createdText = text(root.get("createdAt"));
            String updatedText = text(root.get("updatedAt"));
            if (createdText == null || updatedText == null) {
                issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID", "Snapshot timestamps missing"));
                return new SnapshotParse(null, issues);
            }
            Instant createdAt = Instant.parse(createdText);
            Instant updatedAt = Instant.parse(updatedText);
            long step = root.get("step") == null ? 0L : root.get("step").asLong();
            JsonNode stateNode = root.get("state");
            if (stateNode == null || !stateNode.isObject()) {
                issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID", "State block is missing"));
                return new SnapshotParse(null, issues);
            }
            String sceneId = text(stateNode.get("sceneId"));
            List<String> visited = new ArrayList<>();
            JsonNode visitedNode = stateNode.get("visited");
            if (visitedNode != null && visitedNode.isArray()) {
                for (JsonNode item : visitedNode) {
                    if (item != null && item.isValueNode()) visited.add(item.asText());
                }
            }
            boolean ended = stateNode.has("ended") && stateNode.get("ended").asBoolean();
            String endReasonText = text(stateNode.get("endReason"));
            EndReason endReason = endReasonText == null ? null : EndReason.valueOf(endReasonText);
            RunState state = new RunState(sceneId, visited, ended, endReason);
            RunSnapshot snapshot = new RunSnapshot(version, text(root.get("runId")), new PackRef(packId, packVersion), locale, seed, inputs, createdAt, updatedAt, step, state);
            return new SnapshotParse(snapshot, issues);
        } catch (Exception exception) {
            issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID", "Snapshot JSON is malformed: " + exception.getMessage()));
            return new SnapshotParse(null, issues);
        }
    }

    private static int parseInt(JsonNode node, List<Issue> issues, String code) {
        if (node == null || !node.isIntegralNumber()) {
            if (node != null) issues.add(new Issue(Issue.Severity.ERROR, code, "Expected numeric value"));
            return 0;
        }
        return node.asInt();
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    public record SnapshotParse(RunSnapshot snapshot, List<Issue> issues) {}
}
