package com.capybee.engine.content.json;

import com.capybee.engine.content.Block;
import com.capybee.engine.content.BlockKind;
import com.capybee.engine.content.Scene;
import com.capybee.engine.content.SceneBranch;
import com.capybee.engine.content.SceneOption;
import com.capybee.engine.content.SceneSet;
import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.Pack;
import com.capybee.engine.pack.PackValidationContext;
import com.capybee.engine.rules.Condition;
import com.capybee.engine.rules.Effect;
import com.capybee.engine.rules.json.RulesParser;
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
        return load(pack, null);
    }

    public static Map<LocaleTag, SceneSet> load(Pack pack, PackValidationContext context) {
        Map<LocaleTag, SceneSet> sets = new LinkedHashMap<>();
        for (String file : pack.manifest().files()) {
            if (!file.startsWith("scenes/")) continue;
            int slash = file.indexOf('/', "scenes/".length());
            if (slash <= 0 || slash == file.length() - 1) continue;
            String localeText = file.substring("scenes/".length(), slash);
            LocaleTag locale = new LocaleTag(localeText);
            String json = pack.file(file).orElseThrow(() -> new IllegalStateException("Missing scene file: " + file));
            JsonNode root = parseJson(json, file);
            if (root == null || !root.isObject()) {
                if (context != null) context.error("SCENE_FIELD_INVALID", file, "", "Expected scenes object");
                continue;
            }
            warnUnknown(root, context, file, "", "schemaVersion", "scenes");
            JsonNode schemaVersion = root.get("schemaVersion");
            if (schemaVersion == null || !schemaVersion.isIntegralNumber() || !schemaVersion.canConvertToInt()
                    || schemaVersion.intValue() != 1) {
                if (context != null) context.error("SCENE_FIELD_INVALID", file, "/schemaVersion", "Expected scenes schema version 1");
            }
            JsonNode scenesNode = root.get("scenes");
            if (scenesNode == null || !scenesNode.isArray()) {
                if (context != null) context.error("SCENE_FIELD_INVALID", file, "/scenes", "Expected scene array");
                continue;
            }

            Map<String, Scene> scenes = new LinkedHashMap<>();
            String startId = null;
            for (int sceneIndex = 0; sceneIndex < scenesNode.size(); sceneIndex++) {
                JsonNode node = scenesNode.get(sceneIndex);
                String scenePointer = "/scenes/" + sceneIndex;
                if (!node.isObject()) {
                    if (context != null) context.error("SCENE_FIELD_INVALID", file, scenePointer, "Expected scene object");
                    continue;
                }
                warnUnknown(node, context, file, scenePointer,
                        "id", "start", "blocks", "options", "next", "end", "onEnter", "branches", "outcome");
                String id = text(node, "id");
                if (id == null || !validId(id)) {
                    if (context != null) context.error("SCENE_FIELD_INVALID", file, scenePointer + "/id", "Invalid scene id");
                    continue;
                }
                if (context != null) context.references().define("scene:" + localeText, id, file, scenePointer + "/id");
                boolean isStart = booleanField(node, "start", false, context, file, scenePointer);
                if (isStart && startId == null) startId = id;
                List<Block> blocks = new ArrayList<>();
                JsonNode blocksNode = node.get("blocks");
                if (blocksNode != null && !blocksNode.isArray()) {
                    if (context != null) context.error("SCENE_FIELD_INVALID", file, scenePointer + "/blocks", "Expected block array");
                } else if (blocksNode != null) {
                    for (int blockIndex = 0; blockIndex < blocksNode.size(); blockIndex++) {
                        JsonNode blockNode = blocksNode.get(blockIndex);
                        String blockPointer = scenePointer + "/blocks/" + blockIndex;
                        if (!blockNode.isObject()) {
                            if (context != null) context.error("SCENE_FIELD_INVALID", file, blockPointer, "Expected block object");
                            continue;
                        }
                        warnUnknown(blockNode, context, file, blockPointer, "kind", "speaker", "text", "refs");
                        String kindText = text(blockNode, "kind");
                        BlockKind kind = null;
                        if (kindText != null) {
                            try {
                                kind = BlockKind.valueOf(kindText);
                            } catch (IllegalArgumentException exception) {
                                if (context != null) context.error("SCENE_FIELD_INVALID", file, blockPointer + "/kind", "Unknown block kind");
                            }
                        }
                        String speaker = text(blockNode, "speaker");
                        String text = text(blockNode, "text");
                        List<String> refs = new ArrayList<>();
                        JsonNode refsNode = blockNode.get("refs");
                        if (refsNode != null && !refsNode.isArray()) {
                            if (context != null) context.error("SCENE_FIELD_INVALID", file, blockPointer + "/refs", "Expected reference array");
                        } else if (refsNode != null) {
                            for (int refIndex = 0; refIndex < refsNode.size(); refIndex++) {
                                JsonNode refNode = refsNode.get(refIndex);
                                if (refNode.isString()) refs.add(refNode.asString());
                                else if (context != null) context.error("SCENE_FIELD_INVALID", file,
                                        blockPointer + "/refs/" + refIndex, "Expected string reference");
                            }
                        }
                        if (kind != null && text != null) {
                            blocks.add(new Block(kind, speaker, text, refs));
                        }
                    }
                }
                List<SceneOption> options = new ArrayList<>();
                JsonNode optionsNode = node.get("options");
                if (optionsNode != null && !optionsNode.isArray()) {
                    if (context != null) context.error("SCENE_FIELD_INVALID", file, scenePointer + "/options", "Expected option array");
                } else if (optionsNode != null) {
                    for (int optionIndex = 0; optionIndex < optionsNode.size(); optionIndex++) {
                        JsonNode optionNode = optionsNode.get(optionIndex);
                        String optionPointer = scenePointer + "/options/" + optionIndex;
                        if (!optionNode.isObject()) {
                            if (context != null) context.error("SCENE_FIELD_INVALID", file, optionPointer, "Expected option object");
                            continue;
                        }
                        warnUnknown(optionNode, context, file, optionPointer,
                                "id", "label", "next", "effects", "requires", "unmetHint", "hideWhenUnmet");
                        String optionId = text(optionNode, "id");
                        String label = text(optionNode, "label");
                        String next = text(optionNode, "next");
                        if (next != null && context != null) {
                            context.references().reference("scene:" + localeText, next, file, optionPointer + "/next");
                        }
                        List<Effect> effects = RulesParser.parseEffects(optionNode.get("effects"), pack, context,
                                file, optionPointer + "/effects", true);
                        Condition requires = optionNode.has("requires")
                                ? RulesParser.parseCondition(optionNode.get("requires"), pack, context, file, optionPointer + "/requires") : null;
                        String unmetHint = text(optionNode, "unmetHint");
                        if (optionNode.has("unmetHint")) {
                            if (unmetHint == null || !validStringKey(unmetHint)) {
                                if (context != null) context.error("SCENE_FIELD_INVALID", file, optionPointer + "/unmetHint", "Invalid string key");
                            } else if (context != null) {
                                for (LocaleTag declaredLocale : pack.manifest().locales()) {
                                    context.references().reference("stringKey:" + declaredLocale.value(), unmetHint, file, optionPointer + "/unmetHint");
                                }
                            }
                        }
                        boolean hideWhenUnmet = false;
                        if (optionNode.has("hideWhenUnmet")) {
                            JsonNode hideNode = optionNode.get("hideWhenUnmet");
                            if (hideNode.isBoolean()) hideWhenUnmet = hideNode.booleanValue();
                            else if (context != null) context.error("SCENE_FIELD_INVALID", file,
                                    optionPointer + "/hideWhenUnmet", "Expected boolean");
                        }
                        if (optionId != null && label != null && next != null) {
                            options.add(new SceneOption(optionId, label, next, effects, requires, unmetHint, hideWhenUnmet));
                        }
                    }
                }
                String next = text(node, "next");
                if (next != null && context != null) {
                    context.references().reference("scene:" + localeText, next, file, scenePointer + "/next");
                }
                boolean end = booleanField(node, "end", false, context, file, scenePointer);
                List<Effect> onEnter = RulesParser.parseEffects(node.get("onEnter"), pack, context,
                        file, scenePointer + "/onEnter", true);
                List<SceneBranch> branches = parseBranches(node.get("branches"), pack, context, file,
                        scenePointer + "/branches", localeText);
                String outcome = text(node, "outcome");
                if (node.has("outcome") && (outcome == null || !validId(outcome))) {
                    if (context != null) context.error("SCENE_FIELD_INVALID", file, scenePointer + "/outcome", "Invalid outcome id");
                }
                if (outcome != null && context != null) {
                    context.references().reference("outcome", outcome, file, scenePointer + "/outcome");
                }
                scenes.put(id, new Scene(id, isStart, blocks, options, next, end, onEnter, branches, outcome));
            }
            sets.put(locale, new SceneSet(locale, scenes, startId));
        }
        return sets;
    }

    private static List<SceneBranch> parseBranches(JsonNode node, Pack pack, PackValidationContext context,
                                                    String file, String pointer, String locale) {
        if (node == null) return List.of();
        if (!node.isArray() || node.size() > 32) {
            if (context != null) context.error("SCENE_FIELD_INVALID", file, pointer, "Expected a branch list");
            return List.of();
        }
        List<SceneBranch> branches = new ArrayList<>();
        for (int index = 0; index < node.size(); index++) {
            JsonNode branch = node.get(index);
            String branchPointer = pointer + "/" + index;
            if (branch == null || !branch.isObject()) {
                if (context != null) context.error("SCENE_FIELD_INVALID", file, branchPointer, "Expected branch object");
                continue;
            }
            warnUnknown(branch, context, file, branchPointer, "if", "next");
            Condition condition = RulesParser.parseCondition(branch.get("if"), pack, context, file, branchPointer + "/if");
            String next = text(branch, "next");
            if (next == null || !next.matches("[a-z][a-z0-9]*(?:-[a-z0-9]+)*")) {
                if (context != null) context.error("SCENE_FIELD_INVALID", file, branchPointer + "/next", "Invalid branch target");
            } else if (context != null) {
                context.references().reference("scene:" + locale, next, file, branchPointer + "/next");
            }
            if (condition != null && next != null) branches.add(new SceneBranch(condition, next));
        }
        return List.copyOf(branches);
    }

    private static void warnUnknown(JsonNode node, PackValidationContext context, String file,
                                    String pointer, String... allowedFields) {
        if (context == null) return;
        java.util.Set<String> allowed = java.util.Set.of(allowedFields);
        for (var entry : node.properties()) {
            if (!allowed.contains(entry.getKey())) {
                    context.warn("PACK_UNKNOWN_FIELD", file, pointer + "/" + pointerToken(entry.getKey()), "Unknown scene field");
            }
        }
    }

    private static boolean validStringKey(String value) {
        return value.length() <= 80 && value.matches("[a-z][a-zA-Z0-9._-]*");
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
        return value != null && value.isString() ? value.asString() : null;
    }

    private static boolean booleanField(JsonNode object, String field, boolean fallback,
                                        PackValidationContext context, String file, String pointer) {
        JsonNode value = object.get(field);
        if (value == null) return fallback;
        if (!value.isBoolean()) {
            if (context != null) context.error("SCENE_FIELD_INVALID", file, pointer + "/" + field, "Expected boolean");
            return fallback;
        }
        return value.booleanValue();
    }

    private static boolean validId(String value) {
        return value.length() <= 48 && value.matches("[a-z][a-z0-9]*(?:-[a-z0-9]+)*");
    }

    private static String pointerToken(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }
}
