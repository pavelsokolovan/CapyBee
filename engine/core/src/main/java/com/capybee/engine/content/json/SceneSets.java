package com.capybee.engine.content.json;

import com.capybee.engine.content.Block;
import com.capybee.engine.content.BlockKind;
import com.capybee.engine.content.Scene;
import com.capybee.engine.content.SceneOption;
import com.capybee.engine.content.SceneSet;
import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.Pack;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SceneSets {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SceneSets() {}

    public static Map<LocaleTag, SceneSet> load(Pack pack) {
        Map<LocaleTag, SceneSet> sets = new LinkedHashMap<>();
        for (String file : pack.manifest().files()) {
            if (!file.startsWith("scenes/")) continue;
            int slash = file.indexOf('/', "scenes/".length());
            if (slash <= 0 || slash == file.length() - 1) continue;
            String localeText = file.substring("scenes/".length(), slash);
            LocaleTag locale = new LocaleTag(localeText);
            String json = pack.file(file).orElseThrow(() -> new IllegalStateException("Missing scene file: " + file));
            JsonNode root = parseJson(json, file);
            if (root == null || !root.isObject()) continue;
            JsonNode scenesNode = root.get("scenes");
            if (scenesNode == null || !scenesNode.isArray()) continue;

            Map<String, Scene> scenes = new LinkedHashMap<>();
            String startId = null;
            for (JsonNode node : scenesNode) {
                if (!node.isObject()) continue;
                String id = text(node, "id");
                if (id == null || id.isBlank()) continue;
                boolean isStart = asBoolean(node.get("start"), false);
                if (isStart && startId == null) startId = id;
                List<Block> blocks = new ArrayList<>();
                JsonNode blocksNode = node.get("blocks");
                if (blocksNode != null && blocksNode.isArray()) {
                    for (JsonNode blockNode : blocksNode) {
                        if (!blockNode.isObject()) continue;
                        String kindText = text(blockNode, "kind");
                        BlockKind kind = kindText == null ? null : BlockKind.valueOf(kindText);
                        String speaker = text(blockNode, "speaker");
                        String text = text(blockNode, "text");
                        List<String> refs = new ArrayList<>();
                        JsonNode refsNode = blockNode.get("refs");
                        if (refsNode != null && refsNode.isArray()) {
                            for (JsonNode refNode : refsNode) {
                                if (refNode != null && refNode.isValueNode()) refs.add(refNode.asText());
                            }
                        }
                        if (kind != null && text != null) {
                            blocks.add(new Block(kind, speaker, text, refs));
                        }
                    }
                }
                List<SceneOption> options = new ArrayList<>();
                JsonNode optionsNode = node.get("options");
                if (optionsNode != null && optionsNode.isArray()) {
                    for (JsonNode optionNode : optionsNode) {
                        if (!optionNode.isObject()) continue;
                        String optionId = text(optionNode, "id");
                        String label = text(optionNode, "label");
                        String next = text(optionNode, "next");
                        if (optionId != null && label != null && next != null) {
                            options.add(new SceneOption(optionId, label, next));
                        }
                    }
                }
                String next = text(node, "next");
                boolean end = asBoolean(node.get("end"), false);
                scenes.put(id, new Scene(id, isStart, blocks, options, next, end));
            }
            sets.put(locale, new SceneSet(locale, scenes, startId));
        }
        return sets;
    }

    private static JsonNode parseJson(String json, String file) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static boolean asBoolean(JsonNode node, boolean fallback) {
        return node != null && node.isValueNode() && node.asText().equalsIgnoreCase("true");
    }
}
