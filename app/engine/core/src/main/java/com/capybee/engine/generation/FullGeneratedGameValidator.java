package com.capybee.engine.generation;

import com.capybee.engine.content.Scene;
import com.capybee.engine.content.SceneSet;
import com.capybee.engine.content.json.SceneSets;
import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.Pack;
import com.capybee.engine.pack.PackValidationContext;
import com.capybee.engine.rules.Effect;
import com.capybee.engine.rules.ResultClass;
import com.capybee.engine.rules.Rules;
import com.capybee.engine.rules.json.RulesParser;

import java.util.*;

/** Validates the trusted, provider-independent full-game graph and derives its public profile. */
public final class FullGeneratedGameValidator {
    private static final String FILE = "generation/catalog.json";
    private FullGeneratedGameValidator() {}

    public static GeneratedGameProfile validate(Pack pack, GenerationCatalog catalog, PackValidationContext context) {
        RunContract contract = catalog.runContract();
        if (contract == null) return GeneratedGameProfile.contentOnly();
        if (contract.kind() != RunContractKind.FULL_GAME || contract.contractVersion() != 1) return null;
        Map<LocaleTag, SceneSet> sets = SceneSets.load(pack);
        Rules rules = RulesParser.parse(pack);
        SceneSet base = sets.get(new LocaleTag(catalog.defaultLocale()));
        if (base == null) { error(context, "PACK_RUN_SCENE_UNKNOWN", "No default-locale scene set"); return null; }

        Map<String, Integer> phaseOf = new LinkedHashMap<>();
        Set<String> phaseIds = new HashSet<>();
        Set<PhaseKind> phaseKinds = EnumSet.noneOf(PhaseKind.class);
        for (int i = 0; i < contract.phases().size(); i++) {
            RunPhase phase = contract.phases().get(i);
            if (!phaseIds.add(phase.id()) || !phaseKinds.add(phase.kind()) || phase.kind().ordinal() != i) {
                error(context, "PACK_RUN_PHASE_DUPLICATE", "Phase ids and kinds must be unique and ordered");
            }
            for (String scene : phase.sceneIds()) if (phaseOf.putIfAbsent(scene, i) != null)
                error(context, "PACK_RUN_PHASE_DUPLICATE", "Scene is assigned more than once: " + scene);
        }
        if (contract.phases().size() != PhaseKind.values().length || phaseKinds.size() != PhaseKind.values().length)
            error(context, "PACK_RUN_PHASE_MISSING", "All five phase kinds are required");
        Set<String> utility = new LinkedHashSet<>();
        for (String id : contract.utilitySceneIds()) {
            if (!utility.add(id) || phaseOf.containsKey(id)) error(context, "PACK_RUN_PHASE_DUPLICATE", "Duplicate scene membership: " + id);
        }
        for (String id : phaseOf.keySet()) if (!base.scenes().containsKey(id)) error(context, "PACK_RUN_SCENE_UNKNOWN", "Unknown story scene: " + id);
        for (String id : utility) if (!base.scenes().containsKey(id)) error(context, "PACK_RUN_SCENE_UNKNOWN", "Unknown utility scene: " + id);
        for (String id : base.scenes().keySet()) if (!phaseOf.containsKey(id) && !utility.contains(id))
            error(context, "PACK_RUN_SCENE_UNCLASSIFIED", "Unclassified scene: " + id);

        Scene start = base.scenes().values().stream().filter(Scene::start).findFirst().orElse(null);
        if (start == null || phaseOf.getOrDefault(start.id(), -1) != 0)
            error(context, "PACK_RUN_START_PHASE_INVALID", "Start scene must belong to OPENING");

        Map<String, EndingContract> endings = new LinkedHashMap<>();
        Set<String> outcomes = new HashSet<>();
        for (EndingContract ending : contract.completion().endings()) {
            if (endings.putIfAbsent(ending.sceneId(), ending) != null || !outcomes.add(ending.outcomeId()))
                error(context, "PACK_RUN_PHASE_DUPLICATE", "Ending scenes and outcomes must be unique");
            Scene scene = base.scenes().get(ending.sceneId());
            if (scene == null || phaseOf.getOrDefault(ending.sceneId(), -1) != 4 || !terminal(scene)
                    || !ending.outcomeId().equals(completionOutcome(scene)))
                error(context, "PACK_RUN_ENDING_INVALID", "Invalid declared ending: " + ending.sceneId());
        }
        for (var entry : base.scenes().entrySet()) {
            Scene scene = entry.getValue();
            if (utility.contains(entry.getKey()) && (scene.start() || scene.end() || scene.outcome() != null
                    || scene.onEnter().stream().anyMatch(Effect.EndRun.class::isInstance)))
                error(context, "PACK_RUN_ENDING_INVALID", "Utility scene has story-ending behavior: " + entry.getKey());
            if (phaseOf.containsKey(entry.getKey()) && !endings.containsKey(entry.getKey())
                    && (scene.end() || scene.outcome() != null
                    || scene.onEnter().stream().anyMatch(Effect.EndRun.class::isInstance)))
                error(context, "PACK_RUN_ENDING_INVALID", "Non-ending story scene ends the run: " + entry.getKey());
        }

        validateResults(rules, endings, base, catalog, context);

        validateSlots(base, catalog, phaseOf.keySet(), utility, context);
        validatePlanning(catalog, phaseOf, context);
        validateLocaleParity(sets, catalog, context);

        Map<String, Set<String>> raw = edges(base.scenes());
        Map<String, Set<String>> graph = new LinkedHashMap<>();
        for (String story : phaseOf.keySet()) {
            Set<String> targets = new LinkedHashSet<>();
            for (String target : raw.getOrDefault(story, Set.of())) {
                if (phaseOf.containsKey(target)) targets.add(target);
                else if (utility.contains(target)) collapseUtility(target, phaseOf.get(story), raw, phaseOf, utility,
                        new LinkedHashSet<>(), targets, context);
            }
            graph.put(story, targets);
            for (String target : targets) {
                int from = phaseOf.get(story), to = phaseOf.get(target);
                if (to < from) error(context, "PACK_RUN_ROUTE_BACKWARDS", "Route moves backwards: " + story + " -> " + target);
                if (to > from + 1) error(context, "PACK_RUN_ROUTE_DEAD_END", "Route skips a required phase: " + story + " -> " + target);
            }
        }
        if (hasCycle(graph)) error(context, "PACK_RUN_ROUTE_CYCLIC", "Story graph contains a cycle");
        if (start == null) return null;
        Set<String> reachable = reachable(start.id(), graph);
        for (String id : phaseOf.keySet()) if (!reachable.contains(id)) error(context, "PACK_RUN_SCENE_UNREACHABLE", "Unreachable story scene: " + id);
        for (String id : endings.keySet()) if (!reachable.contains(id)) error(context, "PACK_RUN_SCENE_UNREACHABLE", "Unreachable ending: " + id);
        for (String id : reachable) {
            if (graph.getOrDefault(id, Set.of()).isEmpty() && !endings.containsKey(id))
                error(context, "PACK_RUN_ROUTE_DEAD_END", "Reachable terminal scene is not a declared ending: " + id);
            if (!canReachEnding(id, graph, endings.keySet(), new HashMap<>()))
                error(context, "PACK_RUN_ROUTE_DEAD_END", "Scene cannot reach a declared ending: " + id);
        }
        boolean meaningfulChoice = reachable.stream().anyMatch(id -> phaseOf.get(id) < 3 && base.scenes().get(id).options().size() >= 2);
        if (!meaningfulChoice) error(context, "PACK_RUN_CHOICE_REQUIRED", "A pre-resolution choice is required");
        if (context.hasErrors()) return null;

        PathBounds bounds = bounds(start.id(), graph, endings.keySet(), new HashMap<>());
        if (bounds.min < contract.length().minimumStoryTransitions() || bounds.max > contract.length().maximumStoryTransitions()
                || bounds.max > 32) error(context, "PACK_RUN_LENGTH_INVALID", "Derived route length violates contract bounds");
        int startup = (int) catalog.stages().stream().filter(stage -> stage.stage() != GenerationCatalog.Stage.SCENE).count();
        int calls = startup + bounds.min + 1;
        if (catalog.limits().totalCallsPerRun() < calls || catalog.limits().callsPerStage() < 1)
            error(context, "PACK_RUN_BUDGET_INSUFFICIENT", "Generation budget cannot attempt the shortest route");
        if (context.hasErrors()) return null;
        return new GeneratedGameProfile(GeneratedGameProfile.Kind.FULL_GAME, contract.contractVersion(),
                contract.phases().stream().map(p -> new PhaseProfile(p.id(), p.kind(), p.sceneIds().size())).toList(),
                reachable.size(), endings.size(), bounds.min, bounds.max, calls);
    }

    private static void validateResults(Rules rules, Map<String, EndingContract> endings, SceneSet scenes,
                                        GenerationCatalog catalog, PackValidationContext context) {
        Set<String> used = endings.values().stream().map(EndingContract::outcomeId)
                .collect(java.util.stream.Collectors.toSet());
        boolean hasSuccess = rules.outcomes().stream().anyMatch(o -> o.resultClass() == ResultClass.SUCCESS);
        boolean hasOther = rules.outcomes().stream().anyMatch(o -> o.resultClass() != ResultClass.SUCCESS);
        Set<String> labels = new HashSet<>();
        if (rules.outcomes().size() < 2 || rules.outcomes().size() > 8 || !hasSuccess || !hasOther
                || rules.outcomes().stream().anyMatch(o -> o.labelKey() == null || !used.contains(o.id())
                    || !labels.add(o.labelKey()))) {
            error(context, "PACK_RESULT_OUTCOME_INVALID", "Full-game outcomes need unique labels, complete ending coverage, and distinct result classes");
        }
        boolean endRun = scenes.scenes().values().stream().anyMatch(scene ->
                scene.onEnter().stream().anyMatch(Effect.EndRun.class::isInstance)
                || scene.options().stream().flatMap(option -> option.effects().stream()).anyMatch(Effect.EndRun.class::isInstance));
        if (endRun || rules.objectives().stream().flatMap(objective -> objective.onComplete().stream())
                .anyMatch(Effect.EndRun.class::isInstance)) {
            error(context, "PACK_RESULT_END_RUN_FORBIDDEN", "Full-game contract v1 cannot use END_RUN");
        }
        Set<String> endingScenes = endings.keySet();
        for (GenerationCatalog.SceneSlot slot : catalog.sceneSlots()) {
            if (endingScenes.contains(slot.sceneId()) && slot.generationTiming() != GenerationCatalog.GenerationTiming.ON_ENTRY) {
                error(context, "PACK_RESULT_PREFETCH_INVALID", "Ending scenes must use ON_ENTRY generation: " + slot.sceneId());
            }
        }
    }

    private static void validatePlanning(GenerationCatalog catalog, Map<String, Integer> phaseOf,
                                         PackValidationContext context) {
        GenerationCatalog.TemplateDefinition scenario = catalog.templates().stream()
                .filter(t -> t.stage() == GenerationCatalog.Stage.SCENARIO).findFirst().orElse(null);
        GenerationCatalog.TemplateDefinition cast = catalog.templates().stream()
                .filter(t -> t.stage() == GenerationCatalog.Stage.CAST).findFirst().orElse(null);
        GenerationCatalog.TemplateDefinition quest = catalog.templates().stream()
                .filter(t -> t.stage() == GenerationCatalog.Stage.QUEST).findFirst().orElse(null);
        if (cast == null || cast.actorSlots().isEmpty() || cast.actorSlots().size() > 12 || !unique(cast.actorSlots()))
            error(context, "PACK_GENERATION_CAST_INVALID", "Full games require 1-12 unique actor slots");
        if (quest == null || quest.objectiveIds().isEmpty() || quest.objectiveIds().size() > 12
                || !unique(quest.objectiveIds()))
            error(context, "PACK_GENERATION_QUEST_INVALID", "Full games require 1-12 unique generated objectives");
        for (GenerationCatalog.SceneSlot slot : catalog.sceneSlots()) {
            GenerationCatalog.TemplateDefinition scene = catalog.templates().stream()
                    .filter(t -> t.id().equals(slot.templateId())).findFirst().orElse(null);
            String expectedPhase = phaseOf.containsKey(slot.sceneId())
                    ? catalog.runContract().phases().get(phaseOf.get(slot.sceneId())).id() : null;
            boolean valid = expectedPhase != null && expectedPhase.equals(slot.phaseId())
                    && slot.storyRole() != null && scene != null
                    && scene.storyRoles().contains(slot.storyRole())
                    && scenario != null && scenario.storyRoles().contains(slot.storyRole())
                    && cast != null && cast.actorSlots().containsAll(slot.actorSlots())
                    && quest != null && quest.objectiveIds().containsAll(slot.objectiveIds())
                    && scene.toneTags().containsAll(slot.toneTags())
                    && scenario.toneTags().containsAll(slot.toneTags())
                    && unique(slot.actorSlots()) && unique(slot.objectiveIds()) && unique(slot.toneTags());
            if (!valid) error(context, "PACK_GENERATION_SLOT_PLAN_INVALID", "Invalid planning metadata: " + slot.sceneId());
        }
        if (scenario == null || scenario.storyRoles().isEmpty())
            error(context, "PACK_GENERATION_ROLE_INVALID", "Scenario story roles are required");
    }

    private static boolean unique(List<String> values) { return new HashSet<>(values).size() == values.size(); }

    private static void validateSlots(SceneSet set, GenerationCatalog catalog, Set<String> story, Set<String> utility,
                                      PackValidationContext context) {
        Map<String, List<GenerationCatalog.SceneSlot>> byScene = new HashMap<>();
        catalog.sceneSlots().forEach(slot -> byScene.computeIfAbsent(slot.sceneId(), ignored -> new ArrayList<>()).add(slot));
        for (String id : story) {
            List<GenerationCatalog.SceneSlot> slots = byScene.getOrDefault(id, List.of());
            if (slots.isEmpty()) error(context, "PACK_RUN_SCENE_SLOT_MISSING", "Missing scene slot: " + id);
            else if (slots.size() > 1) error(context, "PACK_RUN_SCENE_SLOT_DUPLICATE", "Duplicate scene slot: " + id);
            else {
                List<String> expected = set.scenes().get(id).options().stream().map(option -> option.id()).toList();
                if (!expected.equals(slots.getFirst().choiceIds()))
                    error(context, "PACK_RUN_CHOICE_SET_MISMATCH", "Scene choice order differs from its slot: " + id);
            }
        }
        for (String id : utility) if (byScene.containsKey(id)) error(context, "PACK_RUN_SCENE_SLOT_DUPLICATE", "Utility scene has a slot: " + id);
    }

    private static void validateLocaleParity(Map<LocaleTag, SceneSet> sets, GenerationCatalog catalog,
                                             PackValidationContext context) {
        SceneSet base = sets.get(new LocaleTag(catalog.defaultLocale()));
        for (String locale : catalog.supportedLocales()) {
            SceneSet candidate = sets.get(new LocaleTag(locale));
            if (candidate == null || !base.scenes().keySet().equals(candidate.scenes().keySet())) {
                error(context, "PACK_RUN_LOCALE_GRAPH_MISMATCH", "Scene ids differ for locale " + locale); continue;
            }
            for (String id : base.scenes().keySet()) if (!signature(base.scenes().get(id)).equals(signature(candidate.scenes().get(id)))) {
                error(context, "PACK_RUN_LOCALE_GRAPH_MISMATCH", "Trusted scene graph differs for locale " + locale + ": " + id); break;
            }
        }
    }

    private static List<Object> signature(Scene s) {
        return List.of(s.start(), s.end(), s.next() == null ? "" : s.next(), s.outcome() == null ? "" : s.outcome(),
                s.options().stream().map(o -> Arrays.asList(o.id(), o.next(), o.effects(), o.requires(), o.hideWhenUnmet())).toList(),
                s.branches(), s.onEnter(), s.inputMode() == null ? "" : s.inputMode(), s.maxChars());
    }

    private static Map<String, Set<String>> edges(Map<String, Scene> scenes) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (Scene scene : scenes.values()) {
            Set<String> next = new LinkedHashSet<>();
            if (scene.next() != null) next.add(scene.next());
            scene.options().forEach(option -> next.add(option.next()));
            scene.branches().forEach(branch -> next.add(branch.next()));
            result.put(scene.id(), next);
        }
        return result;
    }

    private static void collapseUtility(String id, int originPhase, Map<String, Set<String>> raw,
                                        Map<String, Integer> phaseOf, Set<String> utility, Set<String> visiting,
                                        Set<String> targets, PackValidationContext context) {
        if (!visiting.add(id)) { error(context, "PACK_RUN_ROUTE_CYCLIC", "Utility graph contains a cycle"); return; }
        Set<String> next = raw.getOrDefault(id, Set.of());
        if (next.isEmpty()) error(context, "PACK_RUN_ROUTE_DEAD_END", "Utility path does not return to its story phase: " + id);
        for (String target : next) {
            if (utility.contains(target)) collapseUtility(target, originPhase, raw, phaseOf, utility, visiting, targets, context);
            else if (phaseOf.getOrDefault(target, -1) != originPhase)
                error(context, "PACK_RUN_ROUTE_BACKWARDS", "Utility path bridges story phases: " + id);
            else targets.add(target);
        }
        visiting.remove(id);
    }

    private static boolean terminal(Scene scene) { return scene.next() == null && scene.options().isEmpty() && scene.branches().isEmpty(); }
    private static String completionOutcome(Scene scene) {
        if (scene.outcome() != null) return scene.outcome();
        return scene.onEnter().stream().filter(Effect.EndRun.class::isInstance).map(Effect.EndRun.class::cast)
                .map(Effect.EndRun::outcome).findFirst().orElse(null);
    }
    private static boolean hasCycle(Map<String, Set<String>> graph) {
        Set<String> done = new HashSet<>(), active = new HashSet<>();
        for (String id : graph.keySet()) if (cycle(id, graph, done, active)) return true;
        return false;
    }
    private static boolean cycle(String id, Map<String, Set<String>> graph, Set<String> done, Set<String> active) {
        if (active.contains(id)) return true; if (done.contains(id)) return false; active.add(id);
        for (String next : graph.getOrDefault(id, Set.of())) if (cycle(next, graph, done, active)) return true;
        active.remove(id); done.add(id); return false;
    }
    private static Set<String> reachable(String start, Map<String, Set<String>> graph) {
        Set<String> seen = new LinkedHashSet<>(); Deque<String> todo = new ArrayDeque<>(); todo.add(start);
        while (!todo.isEmpty()) { String id = todo.removeFirst(); if (seen.add(id)) todo.addAll(graph.getOrDefault(id, Set.of())); }
        return seen;
    }
    private static boolean canReachEnding(String id, Map<String, Set<String>> graph, Set<String> endings, Map<String, Boolean> memo) {
        if (endings.contains(id)) return true; if (memo.containsKey(id)) return memo.get(id);
        memo.put(id, false);
        boolean result = graph.getOrDefault(id, Set.of()).stream().anyMatch(next -> canReachEnding(next, graph, endings, memo));
        memo.put(id, result); return result;
    }
    private static PathBounds bounds(String id, Map<String, Set<String>> graph, Set<String> endings, Map<String, PathBounds> memo) {
        if (endings.contains(id)) return new PathBounds(0, 0); if (memo.containsKey(id)) return memo.get(id);
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (String next : graph.getOrDefault(id, Set.of())) { PathBounds b = bounds(next, graph, endings, memo); min = Math.min(min, b.min + 1); max = Math.max(max, b.max + 1); }
        PathBounds result = new PathBounds(min, max); memo.put(id, result); return result;
    }
    private static void error(PackValidationContext context, String code, String message) {
        if (context.issues().stream().noneMatch(issue -> issue.code().equals(code) && issue.message().equals(message)))
            context.error(code, FILE, "/runContract", message);
    }
    private record PathBounds(int min, int max) {}
}
