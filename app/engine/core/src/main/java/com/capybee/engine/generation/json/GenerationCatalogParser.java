package com.capybee.engine.generation.json;

import com.capybee.engine.generation.*;
import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.Pack;
import com.capybee.engine.pack.PackLayout;
import com.capybee.engine.pack.PackValidationContext;
import com.capybee.engine.pack.StartInputDef;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GenerationCatalogParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CATALOG_FILE = "generation/catalog.json";
    private static final String TEMPLATES_FILE = "generation/templates.json";

    private GenerationCatalogParser() {}

    public static GenerationCatalog parse(Pack pack) {
        PackValidationContext context = new PackValidationContext();
        JsonNode catalog = read(pack.file(CATALOG_FILE).orElse(null), CATALOG_FILE, context);
        JsonNode templates = read(pack.file(TEMPLATES_FILE).orElse(null), TEMPLATES_FILE, context);
        GenerationCatalog result = validate(pack, catalog, templates, context);
        if (context.hasErrors() || result == null) {
            throw new IllegalArgumentException("Generation catalog is invalid");
        }
        return result;
    }

    public static GenerationCatalog validate(Pack pack, JsonNode catalog, JsonNode templates,
                                             PackValidationContext context) {
        if (!object(catalog, CATALOG_FILE, "GENERATION_CATALOG_INVALID", context)) return null;
        if (!object(templates, TEMPLATES_FILE, "GENERATION_TEMPLATE_INVALID", context)) return null;
        int schemaVersion = integer(catalog.get("schemaVersion"));
        fields(catalog, CATALOG_FILE, "GENERATION_CATALOG_INVALID", context,
                "schemaVersion", "supportedLocales", "defaultLocale", "stages", "sceneSlots", "limits", "runContract");
        fields(templates, TEMPLATES_FILE, "GENERATION_TEMPLATE_INVALID", context, "schemaVersion", "templates");
        if (schemaVersion < 1 || schemaVersion > GenerationCatalog.SUPPORTED_SCHEMA_VERSION) {
            error(context, "GENERATION_SCHEMA_UNSUPPORTED", CATALOG_FILE, "/schemaVersion", "Unsupported generation catalog version");
        }
        if (integer(templates.get("schemaVersion")) < 1 || integer(templates.get("schemaVersion")) > 2) {
            error(context, "GENERATION_SCHEMA_UNSUPPORTED", TEMPLATES_FILE, "/schemaVersion", "Unsupported generation template version");
        }

        List<String> locales = strings(catalog.get("supportedLocales"), CATALOG_FILE, "/supportedLocales", context, 8);
        String defaultLocale = text(catalog.get("defaultLocale"));
        Set<String> localeSet = new HashSet<>(locales);
        if (locales.isEmpty() || localeSet.size() != locales.size()) {
            error(context, "GENERATION_CATALOG_INVALID", CATALOG_FILE, "/supportedLocales", "Locales must be non-empty and unique");
        }
        for (String locale : locales) {
            if (!LocaleTag.isValid(locale) || pack.manifest().locales().stream().noneMatch(item -> item.value().equals(locale))) {
                error(context, "GENERATION_LOCALE_UNSUPPORTED", CATALOG_FILE, "/supportedLocales", "Catalog locale is not declared by the pack");
            }
        }
        if (defaultLocale == null || !localeSet.contains(defaultLocale)
                || pack.manifest().defaultLocale() == null
                || !localeSet.contains(pack.manifest().defaultLocale().value())) {
            error(context, "GENERATION_LOCALE_UNSUPPORTED", CATALOG_FILE, "/defaultLocale", "Default locale must be supported by the catalog");
        }

        Map<String, GenerationCatalog.TemplateDefinition> templateDefinitions = parseTemplates(templates, context);
        List<GenerationCatalog.StageDefinition> stages = parseStages(catalog, templateDefinitions, pack, context);
        List<GenerationCatalog.SceneSlot> slots = parseSlots(catalog, templateDefinitions, pack, context);
        GenerationCatalog.GenerationLimits limits = parseLimits(catalog.get("limits"), context);
        RunContract runContract = parseRunContract(catalog.get("runContract"), schemaVersion, context);
        GenerationSetup setup = parseSetup(pack, runContract, stages, context);
        if (context.hasErrors()) return null;
        return new GenerationCatalog(schemaVersion, locales, defaultLocale, stages,
                new ArrayList<>(templateDefinitions.values()), slots, limits, runContract, setup);
    }

    private static RunContract parseRunContract(JsonNode node, int schemaVersion, PackValidationContext context) {
        if (node == null) return null;
        if (schemaVersion != 2 || !node.isObject()) {
            error(context, "PACK_RUN_CONTRACT_INVALID", CATALOG_FILE, "/runContract",
                    "Run contracts require catalog schema version 2 and an object value");
            return null;
        }
        fields(node, CATALOG_FILE, "PACK_RUN_CONTRACT_INVALID", context,
                "kind", "contractVersion", "phases", "utilitySceneIds", "length", "completion", "setupResource");
        RunContractKind kind = enumValue(RunContractKind.class, text(node.get("kind")));
        int version = integer(node.get("contractVersion"));
        if (version != 1) error(context, "PACK_RUN_CONTRACT_VERSION_UNSUPPORTED", CATALOG_FILE,
                "/runContract/contractVersion", "Unsupported run contract version");
        if (kind == null) error(context, "PACK_RUN_CONTRACT_INVALID", CATALOG_FILE,
                "/runContract/kind", "Unsupported run contract kind");
        List<RunPhase> phases = new ArrayList<>();
        JsonNode phaseNodes = node.get("phases");
        if (phaseNodes == null || !phaseNodes.isArray() || phaseNodes.size() != 5) {
            error(context, "PACK_RUN_PHASE_MISSING", CATALOG_FILE, "/runContract/phases", "Exactly five phases are required");
        } else for (int i = 0; i < phaseNodes.size(); i++) {
            JsonNode phase = phaseNodes.get(i);
            String pointer = "/runContract/phases/" + i;
            if (!object(phase, CATALOG_FILE, "PACK_RUN_CONTRACT_INVALID", context)) continue;
            fields(phase, CATALOG_FILE, "PACK_RUN_CONTRACT_INVALID", context, "id", "kind", "sceneIds");
            String id = text(phase.get("id"));
            PhaseKind phaseKind = enumValue(PhaseKind.class, text(phase.get("kind")));
            List<String> scenes = strings(phase.get("sceneIds"), CATALOG_FILE, pointer + "/sceneIds", context, 64);
            if (!contractId(id) || phaseKind == null || scenes.isEmpty())
                error(context, "PACK_RUN_CONTRACT_INVALID", CATALOG_FILE, pointer, "Invalid phase definition");
            else phases.add(new RunPhase(id, phaseKind, scenes));
        }
        List<String> utility = node.get("utilitySceneIds") == null ? List.of()
                : strings(node.get("utilitySceneIds"), CATALOG_FILE, "/runContract/utilitySceneIds", context, 16);
        JsonNode lengthNode = node.get("length");
        RunLength length = new RunLength(0, 0);
        if (!object(lengthNode, CATALOG_FILE, "PACK_RUN_CONTRACT_INVALID", context)) {
            // error already recorded
        } else {
            fields(lengthNode, CATALOG_FILE, "PACK_RUN_CONTRACT_INVALID", context,
                    "minimumStoryTransitions", "maximumStoryTransitions");
            int min = integer(lengthNode.get("minimumStoryTransitions"));
            int max = integer(lengthNode.get("maximumStoryTransitions"));
            if (min < 4 || min > 31 || max < min || max > 32)
                error(context, "PACK_RUN_LENGTH_INVALID", CATALOG_FILE, "/runContract/length", "Invalid story transition bounds");
            length = new RunLength(min, max);
        }
        List<EndingContract> endings = new ArrayList<>();
        JsonNode completion = node.get("completion");
        if (!object(completion, CATALOG_FILE, "PACK_RUN_CONTRACT_INVALID", context)) {
            // error already recorded
        } else {
            fields(completion, CATALOG_FILE, "PACK_RUN_CONTRACT_INVALID", context, "endings");
            JsonNode endingNodes = completion.get("endings");
            if (endingNodes == null || !endingNodes.isArray() || endingNodes.isEmpty() || endingNodes.size() > 16)
                error(context, "PACK_RUN_ENDING_INVALID", CATALOG_FILE, "/runContract/completion/endings", "Expected 1 to 16 endings");
            else for (int i = 0; i < endingNodes.size(); i++) {
                JsonNode ending = endingNodes.get(i);
                String pointer = "/runContract/completion/endings/" + i;
                if (!object(ending, CATALOG_FILE, "PACK_RUN_ENDING_INVALID", context)) continue;
                fields(ending, CATALOG_FILE, "PACK_RUN_ENDING_INVALID", context, "sceneId", "outcomeId");
                String sceneId = text(ending.get("sceneId"));
                String outcomeId = text(ending.get("outcomeId"));
                if (!validId(sceneId) || !contractId(outcomeId)) error(context, "PACK_RUN_ENDING_INVALID", CATALOG_FILE, pointer, "Invalid ending");
                else endings.add(new EndingContract(sceneId, outcomeId));
            }
        }
        String setupResource = text(node.get("setupResource"));
        if (setupResource != null && !"generation/setup.json".equals(setupResource)) {
            error(context, "PACK_SETUP_REQUIRED", CATALOG_FILE, "/runContract/setupResource", "Setup resource path is invalid");
        }
        return kind == null ? null : new RunContract(kind, version, phases, utility, length,
                new CompletionContract(endings), setupResource);
    }

    private static GenerationSetup parseSetup(Pack pack, RunContract contract,
                                               List<GenerationCatalog.StageDefinition> stages,
                                               PackValidationContext context) {
        boolean full = contract != null && contract.kind() == RunContractKind.FULL_GAME;
        if (!full && (contract == null || contract.setupResource() == null)) return null;
        if (contract == null || contract.setupResource() == null) {
            error(context, "PACK_SETUP_REQUIRED", CATALOG_FILE, "/runContract/setupResource",
                    "A full game requires generation/setup.json");
            return null;
        }
        String file = contract.setupResource();
        if (!pack.manifest().files().contains(file) || pack.file(file).isEmpty()) {
            error(context, "PACK_SETUP_REQUIRED", file, "", "Setup resource is missing");
            return null;
        }
        JsonNode root = read(pack.file(file).orElse(null), file, context);
        if (!object(root, file, "PACK_SETUP_QUESTION_MISMATCH", context)) return null;
        fields(root, file, "PACK_SETUP_QUESTION_MISMATCH", context, "schemaVersion", "questions");
        int version = integer(root.get("schemaVersion"));
        if (version != GenerationSetup.SUPPORTED_SCHEMA_VERSION) {
            error(context, "PACK_SETUP_VERSION_UNSUPPORTED", file, "/schemaVersion", "Unsupported setup schema version");
        }
        JsonNode nodes = root.get("questions");
        if (nodes == null || !nodes.isArray() || nodes.size() > 12) {
            error(context, "PACK_SETUP_QUESTION_MISMATCH", file, "/questions", "Expected 0 to 12 setup questions");
            return null;
        }
        List<StartInputDef> inputs = pack.manifest().startInputs();
        if (nodes.size() != inputs.size()) {
            error(context, "PACK_SETUP_QUESTION_MISMATCH", file, "/questions", "Questions must exactly cover start inputs");
        }
        Set<GenerationCatalog.Stage> configured = new HashSet<>();
        stages.forEach(stage -> configured.add(stage.stage()));
        List<GenerationSetup.SetupQuestion> questions = new ArrayList<>();
        int freeTextCount = 0;
        for (int i = 0; i < nodes.size(); i++) {
            JsonNode node = nodes.get(i); String pointer = "/questions/" + i;
            if (!object(node, file, "PACK_SETUP_QUESTION_MISMATCH", context)) continue;
            fields(node, file, "PACK_SETUP_QUESTION_MISMATCH", context,
                    "inputId", "helpKey", "stages", "defaultValues", "options", "freeTextNoticeKey");
            String inputId = text(node.get("inputId"));
            StartInputDef input = i < inputs.size() ? inputs.get(i) : null;
            if (input == null || !input.id().equals(inputId)) {
                error(context, "PACK_SETUP_QUESTION_MISMATCH", file, pointer + "/inputId",
                        "Question order must match manifest start inputs");
                continue;
            }
            String help = text(node.get("helpKey"));
            List<String> stageNames = strings(node.get("stages"), file, pointer + "/stages", context, 2);
            List<GenerationCatalog.Stage> routed = new ArrayList<>();
            for (String name : stageNames) {
                GenerationCatalog.Stage stage = stageName(name);
                if ((stage != GenerationCatalog.Stage.THEME && stage != GenerationCatalog.Stage.SCENARIO)
                        || !configured.contains(stage) || routed.contains(stage)) {
                    error(context, "PACK_SETUP_STAGE_INVALID", file, pointer + "/stages", "Setup stage is absent, duplicate, or forbidden");
                } else routed.add(stage);
            }
            if (routed.isEmpty()) error(context, "PACK_SETUP_STAGE_INVALID", file, pointer + "/stages", "At least one stage is required");
            List<String> defaults = node.get("defaultValues") == null ? List.of()
                    : strings(node.get("defaultValues"), file, pointer + "/defaultValues", context, 8);
            List<GenerationSetup.SetupOption> options = new ArrayList<>();
            JsonNode optionNodes = node.get("options");
            if (optionNodes != null) {
                int minimumOptions = input instanceof StartInputDef.FreeTextInput ? 0 : 1;
                if (!optionNodes.isArray() || optionNodes.size() < minimumOptions || optionNodes.size() > 32) {
                    error(context, "PACK_SETUP_OPTION_INVALID", file, pointer + "/options", "Expected a bounded option list");
                } else for (int j = 0; j < optionNodes.size(); j++) {
                    JsonNode option = optionNodes.get(j); String op = pointer + "/options/" + j;
                    if (!object(option, file, "PACK_SETUP_OPTION_INVALID", context)) continue;
                    fields(option, file, "PACK_SETUP_OPTION_INVALID", context, "value", "labelKey");
                    String value = text(option.get("value")); String label = text(option.get("labelKey"));
                    if (value == null || label == null) error(context, "PACK_SETUP_OPTION_INVALID", file, op, "Option is invalid");
                    else options.add(new GenerationSetup.SetupOption(value, label));
                }
            }
            String notice = text(node.get("freeTextNoticeKey"));
            if (input instanceof StartInputDef.FreeTextInput freeText) {
                freeTextCount++;
                if (freeText.required() || freeText.maxChars() < 1 || freeText.maxChars() > 300
                        || notice == null || !defaults.isEmpty() || !options.isEmpty()) {
                    error(context, "PACK_SETUP_FREE_TEXT_INVALID", file, pointer, "Full-game free text must be optional, bounded, and have a notice");
                }
            } else {
                if (notice != null) error(context, "PACK_SETUP_FREE_TEXT_INVALID", file, pointer + "/freeTextNoticeKey", "Notice is only valid for free text");
                List<String> declared = input instanceof StartInputDef.EnumInput e ? e.values()
                        : ((StartInputDef.EnumSetInput) input).values();
                if (!options.stream().map(GenerationSetup.SetupOption::value).toList().equals(declared)) {
                    error(context, "PACK_SETUP_OPTION_INVALID", file, pointer + "/options", "Options must match declared values in order");
                }
                boolean defaultsValid = new HashSet<>(defaults).size() == defaults.size() && declared.containsAll(defaults);
                if (input instanceof StartInputDef.EnumInput && (defaults.size() > 1 || input.required() && defaults.size() != 1)) defaultsValid = false;
                if (input instanceof StartInputDef.EnumSetInput set && (defaults.size() > set.maxItems() || input.required() && defaults.isEmpty())) defaultsValid = false;
                if (!defaultsValid) error(context, "PACK_SETUP_DEFAULT_INVALID", file, pointer + "/defaultValues", "Defaults do not match the input declaration");
            }
            validateSetupString(pack, help, 240, file, pointer + "/helpKey", context);
            validateSetupString(pack, input.labelKey(), 120, file, pointer + "/inputId", context);
            if (notice != null) validateSetupString(pack, notice, 240, file, pointer + "/freeTextNoticeKey", context);
            options.forEach(option -> validateSetupString(pack, option.labelKey(), 120, file, pointer + "/options", context));
            if (help != null) questions.add(new GenerationSetup.SetupQuestion(inputId, help, routed, defaults, options, notice));
        }
        if (freeTextCount > 1) error(context, "PACK_SETUP_FREE_TEXT_INVALID", file, "/questions", "At most one free-text question is supported");
        return new GenerationSetup(version, questions);
    }

    private static void validateSetupString(Pack pack, String key, int max, String file, String pointer,
                                            PackValidationContext context) {
        if (key == null) { error(context, "PACK_SETUP_STRING_MISSING", file, pointer, "String key is missing"); return; }
        for (LocaleTag locale : pack.manifest().locales()) {
            JsonNode strings = read(pack.file("strings/" + locale.value() + ".json").orElse(null), file, null);
            String value = strings == null ? null : text(strings.get(key));
            if (value == null || value.isBlank() || value.codePointCount(0, value.length()) > max) {
                error(context, "PACK_SETUP_STRING_MISSING", file, pointer, "Localized setup string is missing or too long");
            }
        }
    }

    private static boolean contractId(String id) {
        return id != null && id.matches("[a-z][a-z0-9-]{0,63}");
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        if (value == null) return null;
        try { return Enum.valueOf(type, value); } catch (IllegalArgumentException exception) { return null; }
    }

    private static Map<String, GenerationCatalog.TemplateDefinition> parseTemplates(JsonNode root, PackValidationContext context) {
        Map<String, GenerationCatalog.TemplateDefinition> templates = new LinkedHashMap<>();
        JsonNode items = root.get("templates");
        if (items == null || !items.isArray() || items.isEmpty() || items.size() > 64) {
            error(context, "GENERATION_TEMPLATE_INVALID", TEMPLATES_FILE, "/templates", "Expected 1 to 64 templates");
            return templates;
        }
        for (int index = 0; index < items.size(); index++) {
            JsonNode item = items.get(index);
            String pointer = "/templates/" + index;
            if (!item.isObject()) {
                error(context, "GENERATION_TEMPLATE_INVALID", TEMPLATES_FILE, pointer, "Expected template object");
                continue;
            }
                fields(item, TEMPLATES_FILE, "GENERATION_TEMPLATE_INVALID", context,
                    "id", "stage", "actorSlots", "objectiveIds", "toneTags", "storyRoles");
            String id = text(item.get("id"));
            GenerationCatalog.Stage stage = stage(item.get("stage"), TEMPLATES_FILE, pointer + "/stage", context);
                List<String> actorSlots = optionalStrings(item.get("actorSlots"), pointer + "/actorSlots", context, 12);
                List<String> objectiveIds = optionalStrings(item.get("objectiveIds"), pointer + "/objectiveIds", context, 12);
                List<String> toneTags = optionalStrings(item.get("toneTags"), pointer + "/toneTags", context, 8);
                List<String> storyRoles = optionalStrings(item.get("storyRoles"), pointer + "/storyRoles", context, 24);
            if (!validId(id) || stage == null) {
                error(context, "GENERATION_TEMPLATE_INVALID", TEMPLATES_FILE, pointer, "Template id or stage is invalid");
                continue;
            }
            if ((stage != GenerationCatalog.Stage.CAST && !actorSlots.isEmpty())
                    || (stage != GenerationCatalog.Stage.QUEST && !objectiveIds.isEmpty())
                    || (stage != GenerationCatalog.Stage.THEME && stage != GenerationCatalog.Stage.SCENARIO
                        && stage != GenerationCatalog.Stage.SCENE && !toneTags.isEmpty())
                    || (stage != GenerationCatalog.Stage.SCENARIO && stage != GenerationCatalog.Stage.SCENE
                        && !storyRoles.isEmpty())
                    || new HashSet<>(actorSlots).size() != actorSlots.size()
                    || new HashSet<>(objectiveIds).size() != objectiveIds.size()
                    || new HashSet<>(toneTags).size() != toneTags.size()
                    || new HashSet<>(storyRoles).size() != storyRoles.size()
                    || storyRoles.stream().anyMatch(role -> !contractId(role))) {
                error(context, "GENERATION_TEMPLATE_INVALID", TEMPLATES_FILE, pointer, "Template slots or vocabularies do not match its stage");
            }
            GenerationCatalog.TemplateDefinition definition = new GenerationCatalog.TemplateDefinition(
                    id, stage, actorSlots, objectiveIds, toneTags, storyRoles);
            if (templates.putIfAbsent(id, definition) != null) {
                error(context, "GENERATION_TEMPLATE_INVALID", TEMPLATES_FILE, pointer + "/id", "Duplicate template id");
            }
        }
        return templates;
    }

    private static List<GenerationCatalog.StageDefinition> parseStages(
            JsonNode root, Map<String, GenerationCatalog.TemplateDefinition> templates, Pack pack, PackValidationContext context) {
        JsonNode items = root.get("stages");
        List<GenerationCatalog.StageDefinition> stages = new ArrayList<>();
        if (items == null || !items.isArray()) {
            error(context, "GENERATION_CATALOG_INVALID", CATALOG_FILE, "/stages", "Expected stage array");
            return stages;
        }
        Map<GenerationCatalog.Stage, Integer> positions = new HashMap<>();
        Set<GenerationCatalog.Stage> required = Set.of(GenerationCatalog.Stage.SCENARIO,
                GenerationCatalog.Stage.CAST, GenerationCatalog.Stage.QUEST, GenerationCatalog.Stage.SCENE);
        for (int index = 0; index < items.size(); index++) {
            JsonNode item = items.get(index);
            String pointer = "/stages/" + index;
            if (!item.isObject()) {
                error(context, "GENERATION_CATALOG_INVALID", CATALOG_FILE, pointer, "Expected stage object");
                continue;
            }
            fields(item, CATALOG_FILE, "GENERATION_CATALOG_INVALID", context,
                    "stage", "templateId", "dependencies", "fallbackResource");
            GenerationCatalog.Stage stage = stage(item.get("stage"), CATALOG_FILE, pointer + "/stage", context);
            String templateId = text(item.get("templateId"));
            String fallback = text(item.get("fallbackResource"));
            List<String> dependencyNames = strings(item.get("dependencies"), CATALOG_FILE, pointer + "/dependencies", context, 5);
            List<GenerationCatalog.Stage> dependencies = new ArrayList<>();
            for (int dependencyIndex = 0; dependencyIndex < dependencyNames.size(); dependencyIndex++) {
                GenerationCatalog.Stage dependency = stageName(dependencyNames.get(dependencyIndex));
                if (dependency == null || dependency == stage || dependencies.contains(dependency)) {
                    error(context, "GENERATION_CATALOG_INVALID", CATALOG_FILE,
                            pointer + "/dependencies/" + dependencyIndex, "Invalid or duplicate stage dependency");
                } else dependencies.add(dependency);
            }
            if (stage == null || !validId(templateId) || !validResource(fallback, pack)) {
                error(context, "GENERATION_CATALOG_INVALID", CATALOG_FILE, pointer, "Stage template or fallback is invalid");
                continue;
            }
            if (positions.putIfAbsent(stage, index) != null) {
                error(context, "GENERATION_CATALOG_INVALID", CATALOG_FILE, pointer + "/stage", "Stage is configured more than once");
            }
            if (templates.get(templateId) == null || templates.get(templateId).stage() != stage) {
                error(context, "GENERATION_TEMPLATE_INVALID", CATALOG_FILE, pointer + "/templateId", "Template is missing or belongs to another stage");
            }
            stages.add(new GenerationCatalog.StageDefinition(stage, templateId, dependencies, fallback));
        }
        for (GenerationCatalog.Stage stage : required) {
            if (!positions.containsKey(stage)) {
                error(context, "GENERATION_CATALOG_INVALID", CATALOG_FILE, "/stages", "Required generation stage is missing");
            }
        }
        for (int index = 0; index < stages.size(); index++) {
            GenerationCatalog.StageDefinition definition = stages.get(index);
            for (GenerationCatalog.Stage dependency : definition.dependencies()) {
                Integer dependencyPosition = positions.get(dependency);
                if (dependencyPosition == null || dependencyPosition >= index) {
                    error(context, "GENERATION_CATALOG_INVALID", CATALOG_FILE, "/stages/" + index + "/dependencies",
                            "Stage dependencies must be configured earlier");
                }
            }
        }
        return stages;
    }

    private static List<GenerationCatalog.SceneSlot> parseSlots(
            JsonNode root, Map<String, GenerationCatalog.TemplateDefinition> templates, Pack pack, PackValidationContext context) {
        JsonNode items = root.get("sceneSlots");
        List<GenerationCatalog.SceneSlot> slots = new ArrayList<>();
        if (items == null || !items.isArray() || items.isEmpty() || items.size() > 64) {
            error(context, "GENERATION_CATALOG_INVALID", CATALOG_FILE, "/sceneSlots", "Expected 1 to 64 scene slots");
            return slots;
        }
        Set<String> ids = new HashSet<>();
        for (int index = 0; index < items.size(); index++) {
            JsonNode item = items.get(index);
            String pointer = "/sceneSlots/" + index;
            if (!item.isObject()) {
                error(context, "GENERATION_CATALOG_INVALID", CATALOG_FILE, pointer, "Expected scene slot object");
                continue;
            }
            fields(item, CATALOG_FILE, "GENERATION_CATALOG_INVALID", context,
                    "slotId", "sceneId", "templateId", "fallbackResource", "choiceIds",
                    "phaseId", "storyRole", "actorSlots", "objectiveIds", "toneTags", "generationTiming");
            String slotId = text(item.get("slotId"));
            String sceneId = text(item.get("sceneId"));
            String templateId = text(item.get("templateId"));
            String fallback = text(item.get("fallbackResource"));
            List<String> choices = strings(item.get("choiceIds"), CATALOG_FILE, pointer + "/choiceIds", context, 8);
            String phaseId = text(item.get("phaseId"));
            String storyRole = text(item.get("storyRole"));
            List<String> actorSlots = optionalStrings(item.get("actorSlots"), pointer + "/actorSlots", context, 8);
            List<String> objectiveIds = optionalStrings(item.get("objectiveIds"), pointer + "/objectiveIds", context, 8);
            List<String> toneTags = optionalStrings(item.get("toneTags"), pointer + "/toneTags", context, 8);
            GenerationCatalog.GenerationTiming timing = item.has("generationTiming")
                    ? enumValue(GenerationCatalog.GenerationTiming.class, text(item.get("generationTiming")))
                    : GenerationCatalog.GenerationTiming.PREFETCHABLE;
            if (timing == null) error(context, "PACK_RESULT_PREFETCH_INVALID", CATALOG_FILE,
                    pointer + "/generationTiming", "Invalid generation timing");
            if (!validId(slotId) || !ids.add(slotId) || !validId(sceneId) || !validId(templateId)
                    || !validResource(fallback, pack) || templates.get(templateId) == null
                    || templates.get(templateId).stage() != GenerationCatalog.Stage.SCENE
                    || new HashSet<>(choices).size() != choices.size() || timing == null) {
                error(context, "GENERATION_CATALOG_INVALID", CATALOG_FILE, pointer, "Scene slot or its template/fallback is invalid");
                continue;
            }
            if (!sceneAndChoicesExist(pack, sceneId, choices)) {
                error(context, "GENERATION_REFERENCE_INVALID", CATALOG_FILE, pointer,
                        "Scene slot must match a trusted scene and its declared choice ids");
            }
            slots.add(new GenerationCatalog.SceneSlot(slotId, sceneId, templateId, fallback, choices,
                    phaseId, storyRole, actorSlots, objectiveIds, toneTags, timing));
        }
        return slots;
    }

    private static GenerationCatalog.GenerationLimits parseLimits(JsonNode node, PackValidationContext context) {
        if (node == null) return GenerationCatalog.GenerationLimits.defaults();
        if (!node.isObject()) {
            error(context, "GENERATION_CATALOG_INVALID", CATALOG_FILE, "/limits", "Expected limits object");
            return GenerationCatalog.GenerationLimits.defaults();
        }
        fields(node, CATALOG_FILE, "GENERATION_CATALOG_INVALID", context,
                "totalCallsPerRun", "callsPerStage", "maxOutputTokens", "maxInputCharacters", "repairAttempts");
        int totalCalls = limit(node, "totalCallsPerRun", 16, 1, 64, context);
        int callsPerStage = limit(node, "callsPerStage", 2, 1, 4, context);
        int outputTokens = limit(node, "maxOutputTokens", 800, 1, 1_500, context);
        int inputChars = limit(node, "maxInputCharacters", 12_000, 1, 32_000, context);
        int repairs = limit(node, "repairAttempts", 1, 0, 1, context);
        return new GenerationCatalog.GenerationLimits(totalCalls, callsPerStage, outputTokens, inputChars, repairs);
    }

    private static int limit(JsonNode node, String key, int fallback, int min, int max, PackValidationContext context) {
        if (!node.has(key)) return fallback;
        int value = integer(node.get(key));
        if (value < min || value > max) {
            error(context, "GENERATION_CATALOG_INVALID", CATALOG_FILE, "/limits/" + key, "Limit is outside supported bounds");
            return fallback;
        }
        return value;
    }

    private static boolean sceneAndChoicesExist(Pack pack, String sceneId, List<String> choices) {
        for (String locale : pack.manifest().locales().stream().map(LocaleTag::value).toList()) {
            boolean found = false;
            for (String path : pack.manifest().files()) {
                if (!path.startsWith("scenes/" + locale + "/")) continue;
                JsonNode root = read(pack.file(path).orElse(null), path, null);
                JsonNode scenes = root == null ? null : root.get("scenes");
                if (scenes == null || !scenes.isArray()) continue;
                for (JsonNode scene : scenes) {
                    if (!sceneId.equals(text(scene.get("id")))) continue;
                    found = true;
                    List<String> actualChoices = new ArrayList<>();
                    JsonNode options = scene.get("options");
                    if (options != null && options.isArray()) {
                        for (JsonNode option : options) actualChoices.add(text(option.get("id")));
                    }
                    if (!actualChoices.equals(choices)) return false;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private static boolean validResource(String path, Pack pack) {
        return PackLayout.isValidFilePath(path) && path.startsWith("generation/fallbacks/")
                && pack.manifest().files().contains(path) && pack.file(path).isPresent();
    }

    private static GenerationCatalog.Stage stage(JsonNode node, String file, String pointer,
                                                 PackValidationContext context) {
        GenerationCatalog.Stage stage = stageName(text(node));
        if (stage == null) error(context, "GENERATION_CATALOG_INVALID", file, pointer, "Unknown generation stage");
        return stage;
    }

    private static GenerationCatalog.Stage stageName(String name) {
        if (name == null) return null;
        try { return GenerationCatalog.Stage.valueOf(name); }
        catch (IllegalArgumentException exception) { return null; }
    }

    private static List<String> strings(JsonNode node, String file, String pointer,
                                        PackValidationContext context, int max) {
        List<String> values = new ArrayList<>();
        if (node == null || !node.isArray() || node.size() > max) {
            error(context, "GENERATION_CATALOG_INVALID", file, pointer, "Expected bounded string array");
            return values;
        }
        for (int index = 0; index < node.size(); index++) {
            String value = text(node.get(index));
            if (value == null || value.isBlank() || value.length() > 80) {
                error(context, "GENERATION_CATALOG_INVALID", file, pointer + "/" + index, "Expected bounded string");
            } else values.add(value);
        }
        return values;
    }

    private static List<String> optionalStrings(JsonNode node, String pointer,
                                                PackValidationContext context, int max) {
        if (node == null) return List.of();
        return strings(node, TEMPLATES_FILE, pointer, context, max);
    }

    private static boolean object(JsonNode node, String file, String code, PackValidationContext context) {
        if (node == null || !node.isObject()) {
            error(context, code, file, "", "Expected object");
            return false;
        }
        return true;
    }

    private static void fields(JsonNode node, String file, String code, PackValidationContext context, String... allowed) {
        Set<String> names = Set.of(allowed);
        node.propertyNames().forEach(name -> {
            if (!names.contains(name)) error(context, code, file, "/" + name, "Unknown property");
        });
    }

    private static boolean validId(String id) {
        return id != null && id.matches("[a-z][a-z0-9]*(?:-[a-z0-9]+)*") && id.length() <= 48;
    }

    private static int integer(JsonNode node) {
        return node != null && node.isIntegralNumber() && node.canConvertToInt() ? node.intValue() : Integer.MIN_VALUE;
    }

    private static String text(JsonNode node) { return node != null && node.isString() ? node.asString() : null; }

    private static JsonNode read(String json, String file, PackValidationContext context) {
        if (json == null) return null;
        try { return MAPPER.readTree(json); }
        catch (RuntimeException exception) {
            if (context != null) error(context, "GENERATION_CATALOG_INVALID", file, "", "Invalid JSON resource");
            return null;
        }
    }

    private static void error(PackValidationContext context, String code, String file, String pointer, String message) {
        context.error(code, file, pointer, message);
    }
}
