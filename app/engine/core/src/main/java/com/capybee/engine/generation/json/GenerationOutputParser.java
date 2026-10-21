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
    private static final Pattern CONTROL = Pattern.compile("(?i)\\b(set|increment|decrement|complete|unlock|route|next|flag|counter|outcome|objective)\\b\\s*[:=]");
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
                case SCENARIO -> fullGame(catalog) ? storyPlan(root, catalog) : scenario(root, catalog);
                case CAST -> cast(root, template, fullGame(catalog));
                case QUEST -> quest(root, template, objectiveIds, fullGame(catalog));
                case SCENE -> fullGame(catalog) ? plannedScene(root, slot) : scene(root, slot, actorIds);
            };
        } catch (ValidationException exception) {
            return Result.invalid(exception.code);
        }
    }

    public String schema(GenerationCatalog.Stage stage) {
        return schema(stage, false);
    }

    public String schema(GenerationCatalog.Stage stage, boolean fullGame) {
        return schema(stage, fullGame, false);
    }

    public String schema(GenerationCatalog.Stage stage, boolean fullGame, boolean endingSlot) {
        Map<String, Object> text = Map.of("type", "string");
        Map<String, Object> document = switch (stage) {
            case THEME -> objectSchema(Map.of("title", text, "premise", text,
                "toneTags", arraySchema(text)));
            case SCENARIO -> fullGame ? objectSchema(Map.of("title", text, "premise", text, "setting", text,
                    "themes", arraySchema(text), "storyPromises", arraySchema(text),
                    "phases", arraySchema(objectSchema(Map.of("phaseId", text, "goal", text,
                        "development", text, "transition", text, "actorSlots", arraySchema(text),
                        "objectiveIds", arraySchema(text), "toneTags", arraySchema(text),
                        "storyRoles", arraySchema(text))))))
                : objectSchema(Map.of("title", text, "premise", text, "setting", text, "beats", arraySchema(text)));
            case CAST -> objectSchema(Map.of("characters", arraySchema(objectSchema(
                fullGame ? Map.of("slotId", text, "name", text, "description", text)
                        : Map.of("name", text, "description", text)))));
            case QUEST -> objectSchema(Map.of("objectives", arraySchema(objectSchema(
                Map.of("objectiveId", text, "title", text, "description", text)))));
            case SCENE -> objectSchema(fullGame && endingSlot ? Map.of("title", text, "narration", text,
                "dialogue", arraySchema(objectSchema(Map.of("actorSlot", text, "text", text))),
                "choices", arraySchema(objectSchema(Map.of("id", text, "label", text))),
                "continuityFacts", arraySchema(text),
                "ending", objectSchema(Map.of("headline", text, "summary", text,
                    "objectiveNotes", arraySchema(objectSchema(Map.of("objectiveId", text, "text", text))),
                    "reflection", text))) : fullGame ? Map.of("title", text, "narration", text,
                "dialogue", arraySchema(objectSchema(Map.of("actorSlot", text, "text", text))),
                "choices", arraySchema(objectSchema(Map.of("id", text, "label", text))),
                "continuityFacts", arraySchema(text)) : Map.of("title", text, "narration", text,
                "dialogue", arraySchema(objectSchema(Map.of("actorId", text, "text", text))),
                "choices", arraySchema(objectSchema(Map.of("id", text, "label", text)))));
        };
        return MAPPER.writeValueAsString(document);
    }

    private boolean fullGame(GenerationCatalog catalog) {
        return catalog.runContract() != null && catalog.runContract().kind() == com.capybee.engine.generation.RunContractKind.FULL_GAME;
    }

    private Result storyPlan(JsonNode root, GenerationCatalog catalog) {
        exactFields(root, "title", "premise", "setting", "themes", "storyPromises", "phases");
        String title = safeText(root, "title", 80), premise = safeText(root, "premise", 500);
        String setting = safeText(root, "setting", 300);
        List<String> themes = uniqueStrings(root, "themes", 8, 32, "GENERATION_STORY_PLAN_REFERENCE_INVALID");
        List<String> promises = uniqueStrings(root, "storyPromises", 8, 180, "GENERATION_STORY_PLAN_PROMISE_INVALID");
        if (promises.isEmpty()) throw invalid("GENERATION_STORY_PLAN_PROMISE_INVALID");
        JsonNode nodes = array(root, "phases", 5);
        if (nodes.size() != catalog.runContract().phases().size()) throw invalid("GENERATION_STORY_PLAN_PHASE_MISMATCH");
        List<GenerationArtifact.PhasePlan> phases = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            JsonNode node = nodes.get(i);
            exactFields(node, "phaseId", "goal", "development", "transition", "actorSlots", "objectiveIds", "toneTags", "storyRoles");
            String phaseId = requiredText(node, "phaseId");
            if (!catalog.runContract().phases().get(i).id().equals(phaseId)) throw invalid("GENERATION_STORY_PLAN_PHASE_MISMATCH");
            List<GenerationCatalog.SceneSlot> slots = catalog.sceneSlots().stream().filter(s -> phaseId.equals(s.phaseId())).toList();
            Set<String> actors = union(slots, GenerationCatalog.SceneSlot::actorSlots);
            Set<String> objectives = union(slots, GenerationCatalog.SceneSlot::objectiveIds);
            Set<String> tones = union(slots, GenerationCatalog.SceneSlot::toneTags);
            Set<String> roles = slots.stream().map(GenerationCatalog.SceneSlot::storyRole).collect(java.util.stream.Collectors.toSet());
            List<String> selectedActors = trusted(node, "actorSlots", 12, actors);
            List<String> selectedObjectives = trusted(node, "objectiveIds", 12, objectives);
            List<String> selectedTones = trusted(node, "toneTags", 8, tones);
            List<String> selectedRoles = trusted(node, "storyRoles", 12, roles);
            if (selectedRoles.isEmpty() || !selectedActors.containsAll(actors) || !selectedObjectives.containsAll(objectives)
                    || !selectedRoles.containsAll(roles)) throw invalid("GENERATION_STORY_PLAN_REFERENCE_INVALID");
            phases.add(new GenerationArtifact.PhasePlan(phaseId, safeText(node, "goal", 240),
                    safeText(node, "development", 240), safeText(node, "transition", 200),
                    selectedActors, selectedObjectives, selectedTones, selectedRoles));
        }
        Set<String> allowedThemes = catalog.templates().stream().filter(t -> t.stage() == GenerationCatalog.Stage.SCENARIO)
                .findFirst().map(t -> Set.copyOf(t.toneTags())).orElse(Set.of());
        if (!allowedThemes.containsAll(themes)) throw invalid("GENERATION_STORY_PLAN_REFERENCE_INVALID");
        return Result.valid(new GenerationArtifact.StoryPlanPayload(title, premise, setting, themes, promises, phases));
    }

    private Set<String> union(List<GenerationCatalog.SceneSlot> slots,
                              java.util.function.Function<GenerationCatalog.SceneSlot, List<String>> getter) {
        Set<String> result = new java.util.LinkedHashSet<>(); slots.forEach(s -> result.addAll(getter.apply(s))); return result;
    }

    private List<String> trusted(JsonNode node, String field, int max, Set<String> allowed) {
        List<String> values = uniqueStrings(node, field, max, 64, "GENERATION_STORY_PLAN_REFERENCE_INVALID");
        if (!allowed.containsAll(values)) throw invalid("GENERATION_STORY_PLAN_REFERENCE_INVALID"); return values;
    }

    private List<String> uniqueStrings(JsonNode node, String field, int max, int cp, String code) {
        List<String> values = stringArray(node, field, max, cp);
        if (new HashSet<>(values).size() != values.size()) throw invalid(code); return values;
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

    private Result cast(JsonNode root, GenerationCatalog.TemplateDefinition template, boolean fullGame) {
        exactFields(root, "characters");
        JsonNode characters = array(root, "characters", template.actorSlots().size());
        List<GenerationArtifact.CastMember> values = new ArrayList<>();
        Set<String> names = new HashSet<>();
        Map<String, GenerationArtifact.CastMember> bySlot = new java.util.LinkedHashMap<>();
        for (int index = 0; index < characters.size(); index++) {
            JsonNode character = characters.get(index);
            boolean explicitSlots = character.has("slotId");
            if (fullGame && !explicitSlots) throw invalid("GENERATION_CAST_COVERAGE_INVALID");
            if (explicitSlots) exactFields(character, "slotId", "name", "description");
            else exactFields(character, "name", "description");
            String slotId = explicitSlots ? requiredText(character, "slotId") : template.actorSlots().get(index);
            if (!template.actorSlots().contains(slotId) || bySlot.containsKey(slotId)) {
                throw invalid("GENERATION_CAST_COVERAGE_INVALID");
            }
            String name = safeText(character, "name", 60);
            String description = safeText(character, "description", 300);
            String normalizedName = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFKC)
                    .toLowerCase(java.util.Locale.ROOT);
            if (!names.add(normalizedName) || identifierLike(name)) throw invalid("GENERATION_CAST_NAME_INVALID");
            bySlot.put(slotId, new GenerationArtifact.CastMember(slotId, null, name, description));
        }
        if (fullGame && bySlot.size() != template.actorSlots().size()) throw invalid("GENERATION_CAST_COVERAGE_INVALID");
        if (fullGame) template.actorSlots().forEach(slotId -> values.add(bySlot.get(slotId)));
        else values.addAll(bySlot.values());
        return Result.valid(new GenerationArtifact.CastPayload(values));
    }

    private Result quest(JsonNode root, GenerationCatalog.TemplateDefinition template, Set<String> objectiveIds,
                         boolean fullGame) {
        exactFields(root, "objectives");
        JsonNode objectives = array(root, "objectives", 12);
        Set<String> allowedObjectives = new HashSet<>(template.objectiveIds());
        if (!fullGame) allowedObjectives.addAll(objectiveIds);
        List<GenerationArtifact.Quest> values = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Set<String> titles = new HashSet<>();
        for (JsonNode objective : objectives) {
            exactFields(objective, "objectiveId", "title", "description");
            String id = requiredText(objective, "objectiveId");
            String title = safeText(objective, "title", 80);
            String description = safeText(objective, "description", 500);
            if (id.isBlank() || (!allowedObjectives.isEmpty() && !allowedObjectives.contains(id))) {
                throw invalid("GENERATION_QUEST_COVERAGE_INVALID");
            }
            if (!seen.add(id)) throw invalid("GENERATION_QUEST_COVERAGE_INVALID");
            String normalizedTitle = java.text.Normalizer.normalize(title, java.text.Normalizer.Form.NFKC)
                    .toLowerCase(java.util.Locale.ROOT);
            if (!titles.add(normalizedTitle)) throw invalid("GENERATION_QUEST_PRESENTATION_INVALID");
            values.add(new GenerationArtifact.Quest(id, title, description));
        }
        if (fullGame && !seen.equals(allowedObjectives)) throw invalid("GENERATION_QUEST_COVERAGE_INVALID");
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

    private Result plannedScene(JsonNode root, GenerationCatalog.SceneSlot slot) {
        boolean endingSlot = "ending".equals(slot.storyRole());
        if (endingSlot) exactFields(root, "title", "narration", "dialogue", "choices", "continuityFacts", "ending");
        else exactFields(root, "title", "narration", "dialogue", "choices", "continuityFacts");
        String title = safeText(root, "title", 80), narration = safeText(root, "narration", 500);
        List<GenerationArtifact.PlannedDialogue> dialogue = new ArrayList<>();
        for (JsonNode line : array(root, "dialogue", 8)) {
            exactFields(line, "actorSlot", "text");
            String actorSlot = requiredText(line, "actorSlot");
            if (!slot.actorSlots().contains(actorSlot)) throw invalid("GENERATION_DIALOGUE_ACTOR_INVALID");
            dialogue.add(new GenerationArtifact.PlannedDialogue(actorSlot, safeText(line, "text", 300)));
        }
        List<GenerationArtifact.ChoiceLabel> choices = parseChoices(root, slot);
        List<String> facts = uniqueStrings(root, "continuityFacts", 4, 140, "GENERATION_SCENE_FACT_INVALID");
        if (facts.stream().mapToInt(s -> s.codePointCount(0, s.length())).sum() > 400
                || facts.stream().anyMatch(f -> CONTROL.matcher(f).find())) throw invalid("GENERATION_SCENE_FACT_INVALID");
        GenerationArtifact.EndingNarrative ending = endingSlot ? parseEnding(root.get("ending"), slot) : null;
        return Result.valid(new GenerationArtifact.PlannedScenePayload(slot.sceneId(), title, narration,
                dialogue, choices, facts, ending));
    }

    private GenerationArtifact.EndingNarrative parseEnding(JsonNode node, GenerationCatalog.SceneSlot slot) {
        if (node == null || !node.isObject()) throw invalid("GENERATION_ENDING_INVALID");
        exactFields(node, "headline", "summary", "objectiveNotes", "reflection");
        String headline = safeText(node, "headline", 100);
        String summary = safeText(node, "summary", 700);
        String reflection = node.has("reflection") && node.get("reflection").isString()
                ? node.get("reflection").asString() : "";
        if (reflection.codePointCount(0, reflection.length()) > 280) throw invalid("GENERATION_ENDING_INVALID");
        JsonNode notesNode = array(node, "objectiveNotes", 8);
        List<GenerationArtifact.ObjectiveNote> notes = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode note : notesNode) {
            exactFields(note, "objectiveId", "text");
            String objectiveId = requiredText(note, "objectiveId");
            if (!slot.objectiveIds().contains(objectiveId) || !seen.add(objectiveId)) {
                throw invalid("GENERATION_ENDING_OBJECTIVE_MISMATCH");
            }
            notes.add(new GenerationArtifact.ObjectiveNote(objectiveId, safeText(note, "text", 240)));
        }
        if (!seen.equals(new HashSet<>(slot.objectiveIds()))) throw invalid("GENERATION_ENDING_OBJECTIVE_MISMATCH");
        return new GenerationArtifact.EndingNarrative(headline, summary, notes, reflection);
    }

    private List<GenerationArtifact.ChoiceLabel> parseChoices(JsonNode root, GenerationCatalog.SceneSlot slot) {
        List<GenerationArtifact.ChoiceLabel> labels = new ArrayList<>(); Set<String> ids = new HashSet<>();
        for (JsonNode choice : array(root, "choices", 8)) {
            exactFields(choice, "id", "label"); String id = requiredText(choice, "id");
            if (!slot.choiceIds().contains(id) || !ids.add(id)) throw invalid("OUTPUT_REFERENCE_INVALID");
            labels.add(new GenerationArtifact.ChoiceLabel(id, safeText(choice, "label", 80)));
        }
        if (!ids.equals(new HashSet<>(slot.choiceIds()))) throw invalid("OUTPUT_REFERENCE_INVALID");
        return labels;
    }

    private boolean identifierLike(String value) {
        return value.matches("(?i)[0-9a-f]{8}-[0-9a-f-]{27,}")
                || value.matches("(?i)(actor|slot|id|cmd|system)[_-].*")
                || value.startsWith("/") || value.matches("[A-Z0-9_]{3,}");
    }

    private Result sceneFields(JsonNode root, GenerationCatalog.SceneSlot slot, Set<String> actorIds) {
        String title = safeText(root, "title", 80), narration = safeText(root, "narration", 500);
        List<GenerationArtifact.Dialogue> lines = new ArrayList<>();
        for (JsonNode line : array(root, "dialogue", 8)) {
            exactFields(line, "actorId", "text"); String actorId = requiredText(line, "actorId");
            if (!actorIds.contains(actorId)) throw invalid("OUTPUT_REFERENCE_INVALID");
            lines.add(new GenerationArtifact.Dialogue(actorId, safeText(line, "text", 300)));
        }
        List<GenerationArtifact.ChoiceLabel> labels = new ArrayList<>(); Set<String> ids = new HashSet<>();
        for (JsonNode choice : array(root, "choices", 8)) {
            exactFields(choice, "id", "label"); String id = requiredText(choice, "id");
            if (!slot.choiceIds().contains(id) || !ids.add(id)) throw invalid("OUTPUT_REFERENCE_INVALID");
            labels.add(new GenerationArtifact.ChoiceLabel(id, safeText(choice, "label", 80)));
        }
        if (!ids.equals(new HashSet<>(slot.choiceIds()))) throw invalid("OUTPUT_REFERENCE_INVALID");
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
