package com.capybee.engine.generation.json;

import com.capybee.engine.generation.GenerationArtifact;
import com.capybee.engine.generation.GenerationCatalog;
import com.capybee.engine.input.PiiScrubber;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class GenerationOutputParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern MARKUP = Pattern.compile("(?is)<\\s*/?\\s*(script|html|iframe|img|svg)|javascript:");
    private final PiiScrubber piiScrubber = new PiiScrubber();

    public Result parse(GenerationCatalog.Stage stage, String json, int maxCharacters,
                        GenerationCatalog catalog, GenerationCatalog.TemplateDefinition template,
                        GenerationCatalog.SceneSlot slot, Set<String> actorIds, Set<String> objectiveIds) {
        if (json == null || json.length() > maxCharacters) return Result.invalid("OUTPUT_SIZE_LIMIT");
        JsonNode root;
        try { root = MAPPER.readTree(json); }
        catch (RuntimeException exception) { return Result.invalid("OUTPUT_JSON_INVALID"); }
        if (root == null || !root.isObject()) return Result.invalid("OUTPUT_SCHEMA_INVALID");
        try {
            return switch (stage) {
                case THEME -> theme(root, template);
                case SCENARIO -> scenario(root, catalog);
                case CAST -> cast(root, template);
                case QUEST -> quest(root, template, objectiveIds);
                case SCENE -> scene(root, slot, actorIds);
            };
        } catch (ValidationException exception) {
            return Result.invalid(exception.code);
        }
    }

    public String schema(GenerationCatalog.Stage stage) {
        Map<String, Object> text = Map.of("type", "string");
        Map<String, Object> document = switch (stage) {
            case THEME -> objectSchema(Map.of("title", text, "premise", text,
                "toneTags", arraySchema(text)));
            case SCENARIO -> objectSchema(Map.of("title", text, "premise", text, "setting", text,
                "beats", arraySchema(text)));
            case CAST -> objectSchema(Map.of("characters", arraySchema(objectSchema(
                Map.of("name", text, "description", text)))));
            case QUEST -> objectSchema(Map.of("objectives", arraySchema(objectSchema(
                Map.of("objectiveId", text, "title", text, "description", text)))));
            case SCENE -> objectSchema(Map.of("title", text, "narration", text,
                "dialogue", arraySchema(objectSchema(Map.of("actorId", text, "text", text))),
                "choices", arraySchema(objectSchema(Map.of("id", text, "label", text)))));
        };
        return MAPPER.writeValueAsString(document);
    }

    private Map<String, Object> objectSchema(Map<String, Object> properties) {
        return Map.of("type", "object", "additionalProperties", false,
            "properties", properties, "required", properties.keySet().stream().sorted().toList());
    }

    private Map<String, Object> arraySchema(Map<String, Object> items) {
        return Map.of("type", "array", "items", items);
    }

    private Result theme(JsonNode root, GenerationCatalog.TemplateDefinition template) {
        exactFields(root, "title", "premise", "toneTags");
        String title = safeText(root, "title", 80);
        String premise = safeText(root, "premise", 500);
        List<String> tags = stringArray(root, "toneTags", 8, 32);
        if (!template.toneTags().containsAll(tags) || new HashSet<>(tags).size() != tags.size()) {
            throw invalid("OUTPUT_REFERENCE_INVALID");
        }
        return Result.valid(new GenerationArtifact.ThemePayload(title, premise, tags));
    }

    private Result scenario(JsonNode root, GenerationCatalog catalog) {
        exactFields(root, "title", "premise", "setting", "beats");
        String title = safeText(root, "title", 80);
        String premise = safeText(root, "premise", 500);
        String setting = safeText(root, "setting", 300);
        List<String> beats = stringArray(root, "beats", 8, 300);
        if (beats.isEmpty()) {
            throw invalid("OUTPUT_REFERENCE_INVALID");
        }
        return Result.valid(new GenerationArtifact.ScenarioPayload(title, premise, setting, beats));
    }

    private Result cast(JsonNode root, GenerationCatalog.TemplateDefinition template) {
        exactFields(root, "characters");
        JsonNode characters = array(root, "characters", template.actorSlots().size());
        List<GenerationArtifact.CastMember> values = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (int index = 0; index < characters.size(); index++) {
            JsonNode character = characters.get(index);
            exactFields(character, "name", "description");
            String name = safeText(character, "name", 60);
            String description = safeText(character, "description", 300);
            if (!names.add(name.toLowerCase(java.util.Locale.ROOT))) throw invalid("OUTPUT_DUPLICATE_ENTRY");
            values.add(new GenerationArtifact.CastMember(template.actorSlots().get(index), null, name, description));
        }
        return Result.valid(new GenerationArtifact.CastPayload(values));
    }

    private Result quest(JsonNode root, GenerationCatalog.TemplateDefinition template, Set<String> objectiveIds) {
        exactFields(root, "objectives");
        JsonNode objectives = array(root, "objectives", 12);
        Set<String> allowedObjectives = new HashSet<>(template.objectiveIds());
        allowedObjectives.addAll(objectiveIds);
        List<GenerationArtifact.Quest> values = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode objective : objectives) {
            exactFields(objective, "objectiveId", "title", "description");
            String id = requiredText(objective, "objectiveId");
            String title = safeText(objective, "title", 80);
            String description = safeText(objective, "description", 500);
            if (id.isBlank() || (!allowedObjectives.isEmpty() && !allowedObjectives.contains(id))) {
                throw invalid("OUTPUT_REFERENCE_INVALID");
            }
            if (!seen.add(id)) throw invalid("OUTPUT_DUPLICATE_ENTRY");
            values.add(new GenerationArtifact.Quest(id, title, description));
        }
        return Result.valid(new GenerationArtifact.QuestPayload(values));
    }

    private Result scene(JsonNode root, GenerationCatalog.SceneSlot slot, Set<String> actorIds) {
        if (slot == null) throw invalid("OUTPUT_REFERENCE_INVALID");
        exactFields(root, "title", "narration", "dialogue", "choices");
        String title = safeText(root, "title", 80);
        String narration = safeText(root, "narration", 500);
        JsonNode dialogue = array(root, "dialogue", 8);
        List<GenerationArtifact.Dialogue> lines = new ArrayList<>();
        for (JsonNode line : dialogue) {
            exactFields(line, "actorId", "text");
            String actorId = requiredText(line, "actorId");
            String text = safeText(line, "text", 300);
            if (!actorIds.contains(actorId)) throw invalid("OUTPUT_REFERENCE_INVALID");
            lines.add(new GenerationArtifact.Dialogue(actorId, text));
        }
        JsonNode choices = array(root, "choices", 8);
        List<GenerationArtifact.ChoiceLabel> labels = new ArrayList<>();
        Set<String> choiceIds = new HashSet<>();
        for (JsonNode choice : choices) {
            exactFields(choice, "id", "label");
            String id = requiredText(choice, "id");
            String label = safeText(choice, "label", 80);
            if (!slot.choiceIds().contains(id) || !choiceIds.add(id)) throw invalid("OUTPUT_REFERENCE_INVALID");
            labels.add(new GenerationArtifact.ChoiceLabel(id, label));
        }
        if (!choiceIds.equals(new HashSet<>(slot.choiceIds()))) throw invalid("OUTPUT_REFERENCE_INVALID");
        return Result.valid(new GenerationArtifact.ScenePayload(slot.sceneId(), title, narration, lines, labels));
    }

    private String safeText(JsonNode node, String field, int maxCodePoints) {
        String value = requiredText(node, field);
        if (value.codePointCount(0, value.length()) > maxCodePoints || value.isBlank()
                || MARKUP.matcher(value).find() || piiScrubber.scrub(value).changed()) throw invalid("OUTPUT_SAFETY_REJECTED");
        return value;
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isString()) throw invalid("OUTPUT_SCHEMA_INVALID");
        return value.asString();
    }

    private List<String> stringArray(JsonNode root, String field, int maxItems, int maxCodePoints) {
        JsonNode values = array(root, field, maxItems);
        List<String> result = new ArrayList<>();
        for (JsonNode value : values) {
            if (!value.isString() || value.asString().codePointCount(0, value.asString().length()) > maxCodePoints
                    || value.asString().isBlank()) throw invalid("OUTPUT_SCHEMA_INVALID");
            if (MARKUP.matcher(value.asString()).find() || piiScrubber.scrub(value.asString()).changed()) {
                throw invalid("OUTPUT_SAFETY_REJECTED");
            }
            result.add(value.asString());
        }
        return result;
    }

    private JsonNode array(JsonNode root, String field, int maxItems) {
        JsonNode array = root.get(field);
        if (array == null || !array.isArray() || array.size() > maxItems) throw invalid("OUTPUT_SCHEMA_INVALID");
        return array;
    }

    private void exactFields(JsonNode node, String... fields) {
        if (node == null || !node.isObject()) throw invalid("OUTPUT_SCHEMA_INVALID");
        Set<String> expected = Set.of(fields);
        Set<String> actual = new HashSet<>();
        node.propertyNames().forEach(actual::add);
        if (!actual.equals(expected)) throw invalid("OUTPUT_SCHEMA_INVALID");
    }

    private ValidationException invalid(String code) { return new ValidationException(code); }

    public record Result(GenerationArtifact.Payload payload, String errorCode) {
        public static Result valid(GenerationArtifact.Payload payload) { return new Result(payload, null); }
        public static Result invalid(String code) { return new Result(null, code); }
    }

    private static final class ValidationException extends RuntimeException {
        final String code;
        ValidationException(String code) { this.code = code; }
    }
}