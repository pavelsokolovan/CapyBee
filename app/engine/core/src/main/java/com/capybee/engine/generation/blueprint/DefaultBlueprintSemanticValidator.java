package com.capybee.engine.generation.blueprint;

import com.capybee.engine.generation.PhaseKind;
import com.capybee.engine.generation.blueprint.ParsedGameBlueprint.*;
import com.capybee.engine.generation.profile.*;
import com.capybee.engine.rules.ResultClass;
import com.capybee.engine.rules.ConditionEvaluator;
import com.capybee.engine.rules.GeneratedMechanicsV1;
import com.capybee.engine.rules.RuleStateView;
import com.capybee.engine.rules.StateEffectSemantics;
import java.util.*;

/** Deterministic, content-safe semantic validator for the closed blueprint v1 contract. */
public final class DefaultBlueprintSemanticValidator implements BlueprintSemanticValidator {
    public static final String CONTRACT_VERSION = GeneratedMechanicsV1.CONTRACT_VERSION;
    public static final String VALIDATOR_VERSION = "semantic-validator-v1";

    @Override
    public Validation validate(ParsedGameBlueprint blueprint, ValidatedGeneratorProfile profile,
                               ResolvedGamePreferences preferences, EffectiveGenerationConstraints constraints) {
        if (!GenerationProfileTrust.validContext(profile, preferences, constraints)) {
            return result(Status.INVALID_CONTEXT, issue("BLUEPRINT_VALIDATION_CONTEXT_INVALID", ""));
        }
        if (blueprint == null) return result(Status.REJECT, issue("BLUEPRINT_CAPABILITY_VIOLATION", ""));
        Checker checker = new Checker(blueprint, profile, preferences, constraints);
        return checker.validate();
    }

    private static Validation result(Status status, BlueprintIssue issue) {
        return new Validation(status, VALIDATOR_VERSION, List.of(issue), null);
    }

    private static BlueprintIssue issue(String code, String pointer) {
        return new BlueprintIssue(code, "blueprint", pointer, "Blueprint semantic validation failed");
    }

    private static final class Checker {
        private final ParsedGameBlueprint blueprint;
        private final ValidatedGeneratorProfile validatedProfile;
        private final ResolvedGamePreferences preferences;
        private final EffectiveGenerationConstraints limits;
        private final ProfileCapabilities capabilities;
        private final ChallengePreset challenge;
        private final Issues issues = new Issues();
        private final Map<String, Actor> actors = new LinkedHashMap<>();
        private final Map<String, Objective> objectives = new LinkedHashMap<>();
        private final Map<String, Scene> scenes = new LinkedHashMap<>();
        private final Map<String, Outcome> outcomes = new LinkedHashMap<>();
        private final Map<String, Counter> counters = new LinkedHashMap<>();
        private final Map<String, Flag> flags = new LinkedHashMap<>();
        private final Map<String, FiniteSet> sets = new LinkedHashMap<>();
        private final Map<String, Integer> sceneIndexes = new LinkedHashMap<>();
        private final Map<String, List<String>> graph = new LinkedHashMap<>();
        private final Set<String> endingIds = new LinkedHashSet<>();
        private Set<String> structurallyReachable = Set.of();
        private int edgeCount;
        private ValidatedMechanicsPlan mechanicsPlan;

        Checker(ParsedGameBlueprint blueprint, ValidatedGeneratorProfile profile,
                ResolvedGamePreferences preferences, EffectiveGenerationConstraints limits) {
            this.blueprint = blueprint;
            this.validatedProfile = profile;
            this.preferences = preferences;
            this.limits = limits;
            this.capabilities = profile.profile().capabilities();
            this.challenge = profile.profile().challengePresets().stream()
                    .filter(value -> value.id().equals(preferences.challengePresetId())).findFirst().orElseThrow();
        }

        Validation validate() {
            envelopeAndIndexes();
            referencesAndMechanics();
            if (!issues.empty()) return reject();
            try { mechanicsPlan = new GeneratedMechanicsBinder().bind(blueprint); }
            catch (IllegalStateException ex) {
                issues.add("BLUEPRINT_MECHANICS_CONTRACT_MISMATCH", "");
                return reject();
            }
            staticGraph();
            coverage();
            if (!issues.empty()) return reject();
            return explore();
        }

        private void envelopeAndIndexes() {
            if (blueprint.schemaVersion() != 1) issues.add("BLUEPRINT_CAPABILITY_VIOLATION", "/schemaVersion");
            if (!Objects.equals(blueprint.locale(), preferences.gameLocale().value()))
                issues.add("BLUEPRINT_CAPABILITY_VIOLATION", "/locale");
            range(blueprint.scenes().size(), limits.minScenes(), limits.maxScenes(), "/scenes");
            range(blueprint.actors().size(), capabilities.minActors(), capabilities.maxActors(), "/actors");
            range(blueprint.objectives().size(), capabilities.minObjectives(), capabilities.maxObjectives(), "/objectives");
            index(blueprint.actors(), Actor::id, actors, "/actors");
            index(blueprint.objectives(), Objective::id, objectives, "/objectives");
            index(blueprint.outcomes(), Outcome::id, outcomes, "/outcomes");
            index(blueprint.state().counters(), Counter::id, counters, "/state/counters");
            index(blueprint.state().flags(), Flag::id, flags, "/state/flags");
            index(blueprint.state().sets(), FiniteSet::id, sets, "/state/sets");
            for (int i = 0; i < blueprint.scenes().size(); i++) {
                Scene scene = blueprint.scenes().get(i); String id = sceneId(scene);
                if (id == null || scenes.putIfAbsent(id, scene) != null)
                    issues.add("BLUEPRINT_CAPABILITY_VIOLATION", "/scenes/" + i + "/id");
                else sceneIndexes.put(id, i);
                if (scene instanceof EndingScene) endingIds.add(id);
            }
            range(endingIds.size(), capabilities.minEndings(), capabilities.maxEndings(), "/scenes");
            range(blueprint.outcomes().size(), capabilities.minEndings(), capabilities.maxEndings(), "/outcomes");
            range(counters.size(), 0, Math.min(limits.maxCounters(), capabilities.maxCounters()), "/state/counters");
            range(flags.size(), 0, Math.min(limits.maxFlags(), capabilities.maxFlags()), "/state/flags");
            range(sets.size(), 0, Math.min(limits.maxSets(), capabilities.maxSets()), "/state/sets");
            validatePhases();
            validateDeclarations();
            validateSceneShapes();
            validateOutcomeShape();
        }

        private <T> void index(List<T> values, java.util.function.Function<T, String> id,
                               Map<String, T> target, String pointer) {
            for (int i = 0; i < values.size(); i++) {
                String value = id.apply(values.get(i));
                if (value == null || target.putIfAbsent(value, values.get(i)) != null)
                    issues.add("BLUEPRINT_CAPABILITY_VIOLATION", pointer + "/" + i + "/id");
            }
        }

        private void validatePhases() {
            if (blueprint.phases().size() != PhaseKind.values().length) {
                issues.add("BLUEPRINT_PHASE_COVERAGE_INVALID", "/phases"); return;
            }
            for (int i = 0; i < blueprint.phases().size(); i++) {
                if (blueprint.phases().get(i).kind() != PhaseKind.values()[i])
                    issues.add("BLUEPRINT_PHASE_COVERAGE_INVALID", "/phases/" + i + "/kind");
            }
        }

        private void validateDeclarations() {
            for (int i = 0; i < blueprint.state().counters().size(); i++) {
                Counter value = blueprint.state().counters().get(i);
                if (value.min() < capabilities.counterMin() || value.max() > capabilities.counterMax()
                        || value.min() > value.max() || value.initial() < value.min() || value.initial() > value.max())
                    issues.add("BLUEPRINT_STATE_BOUNDS_VIOLATED", "/state/counters/" + i);
            }
            for (int i = 0; i < blueprint.state().sets().size(); i++) {
                FiniteSet value = blueprint.state().sets().get(i);
                if (value.values().size() > capabilities.maxSetValues()
                        || new LinkedHashSet<>(value.values()).size() != value.values().size()
                        || new LinkedHashSet<>(value.initial()).size() != value.initial().size()
                        || !value.values().containsAll(value.initial()))
                    issues.add("BLUEPRINT_STATE_BOUNDS_VIOLATED", "/state/sets/" + i);
            }
        }

        private void validateSceneShapes() {
            EnumSet<PhaseKind> represented = EnumSet.noneOf(PhaseKind.class);
            for (int i = 0; i < blueprint.scenes().size(); i++) {
                Scene scene = blueprint.scenes().get(i); PhaseKind phase = scenePhase(scene);
                if (phase != null) represented.add(phase);
                if (scene instanceof DecisionScene decision) {
                    if (phase == PhaseKind.ENDING) issues.add("BLUEPRINT_PHASE_COVERAGE_INVALID", "/scenes/" + i + "/phase");
                    range(decision.choices().size(), 1, capabilities.maxChoicesPerScene(), "/scenes/" + i + "/choices");
                } else if (phase != PhaseKind.ENDING) {
                    issues.add("BLUEPRINT_PHASE_COVERAGE_INVALID", "/scenes/" + i + "/phase");
                }
            }
            for (PhaseKind phase : PhaseKind.values()) if (!represented.contains(phase))
                issues.add("BLUEPRINT_PHASE_COVERAGE_INVALID", "/scenes");
        }

        private void validateOutcomeShape() {
            Map<String, Integer> uses = new LinkedHashMap<>();
            for (int i = 0; i < blueprint.scenes().size(); i++) if (blueprint.scenes().get(i) instanceof EndingScene ending) {
                if (!outcomes.containsKey(ending.outcomeId())) issues.add("BLUEPRINT_OUTCOME_UNKNOWN", "/scenes/" + i + "/outcomeId");
                else uses.merge(ending.outcomeId(), 1, Integer::sum);
            }
            for (int i = 0; i < blueprint.outcomes().size(); i++) {
                Outcome outcome = blueprint.outcomes().get(i);
                if (uses.getOrDefault(outcome.id(), 0) != 1)
                    issues.add("BLUEPRINT_OUTCOME_MAPPING_INVALID", "/outcomes/" + i);
            }
            if (blueprint.outcomes().size() != endingIds.size()) issues.add("BLUEPRINT_OUTCOME_MAPPING_INVALID", "/outcomes");
            boolean success = blueprint.outcomes().stream().anyMatch(o -> o.resultClass() == ResultClass.SUCCESS);
            boolean other = blueprint.outcomes().stream().anyMatch(o -> o.resultClass() != null && o.resultClass() != ResultClass.SUCCESS);
            if (!success || !other) issues.add("BLUEPRINT_OUTCOME_VARIETY_REQUIRED", "/outcomes");
        }

        private void referencesAndMechanics() {
            for (int i = 0; i < blueprint.phases().size(); i++) {
                Phase phase = blueprint.phases().get(i);
                references(phase.actorIds(), actors, "BLUEPRINT_ACTOR_UNKNOWN", "/phases/" + i + "/actorIds");
                references(phase.objectiveIds(), objectives, "BLUEPRINT_OBJECTIVE_UNKNOWN", "/phases/" + i + "/objectiveIds");
            }
            for (int i = 0; i < blueprint.objectives().size(); i++)
                inspectCondition(blueprint.objectives().get(i).completeWhen(), "/objectives/" + i + "/completeWhen", false);
            for (int i = 0; i < blueprint.scenes().size(); i++) {
                Scene scene = blueprint.scenes().get(i);
                references(sceneActors(scene), actors, "BLUEPRINT_ACTOR_UNKNOWN", "/scenes/" + i + "/actorIds");
                references(sceneObjectives(scene), objectives, "BLUEPRINT_OBJECTIVE_UNKNOWN", "/scenes/" + i + "/objectiveIds");
                if (scene instanceof DecisionScene decision) for (int j = 0; j < decision.choices().size(); j++) {
                    Choice choice = decision.choices().get(j); String path = "/scenes/" + i + "/choices/" + j;
                    if (!scenes.containsKey(choice.destinationSceneId())) issues.add("BLUEPRINT_SCENE_UNKNOWN", path + "/destinationSceneId");
                    inspectCondition(choice.requires(), path + "/requires", true);
                    if (choice.effects().size() > Math.min(limits.maxEffectsPerChoice(), capabilities.maxEffectsPerChoice()))
                        issues.add("BLUEPRINT_CAPABILITY_VIOLATION", path + "/effects");
                    for (int k = 0; k < choice.effects().size(); k++) inspectEffect(choice.effects().get(k), path + "/effects/" + k);
                }
            }
        }

        private void references(List<String> values, Map<String, ?> declarations, String code, String pointer) {
            for (int i = 0; i < values.size(); i++) if (!declarations.containsKey(values.get(i))) issues.add(code, pointer + "/" + i);
        }

        private void inspectCondition(Condition root, String pointer, boolean choiceRequirement) {
            if (root == null) return;
            int[] nodes = {0}; inspectCondition(root, pointer, choiceRequirement, 1, nodes);
            if (nodes[0] > capabilities.maxConditionNodes()) issues.add("BLUEPRINT_CONDITION_INVALID", pointer);
        }

        private void inspectCondition(Condition condition, String pointer, boolean choiceRequirement, int depth, int[] nodes) {
            nodes[0]++;
            if (depth > Math.min(limits.maxConditionDepth(), capabilities.maxConditionDepth()))
                issues.add("BLUEPRINT_CONDITION_INVALID", pointer);
            String type = conditionType(condition);
            if (type == null || !limits.allowedConditions().contains(type) || !challenge.allowedConditions().contains(type)
                    || !capabilities.allowedConditions().contains(type)) issues.add("BLUEPRINT_CONDITION_INVALID", pointer);
            if (condition instanceof CounterCondition value) {
                Counter declaration = counters.get(value.counter());
                if (declaration == null) issues.add("BLUEPRINT_STATE_UNKNOWN", pointer + "/counter");
                else if (value.value() < declaration.min() || value.value() > declaration.max()) issues.add("BLUEPRINT_CONDITION_INVALID", pointer + "/value");
            } else if (condition instanceof FlagCondition value) {
                if (!flags.containsKey(value.flag())) issues.add("BLUEPRINT_STATE_UNKNOWN", pointer + "/flag");
            } else if (condition instanceof SetContainsCondition value) {
                FiniteSet declaration = sets.get(value.set());
                if (declaration == null) issues.add("BLUEPRINT_STATE_UNKNOWN", pointer + "/set");
                else if (!declaration.values().contains(value.value())) issues.add("BLUEPRINT_SET_VALUE_UNKNOWN", pointer + "/value");
            } else if (condition instanceof SetSizeCondition value) {
                FiniteSet declaration = sets.get(value.set());
                if (declaration == null) issues.add("BLUEPRINT_STATE_UNKNOWN", pointer + "/set");
                else if (value.size() < 0 || value.size() > declaration.values().size()) issues.add("BLUEPRINT_CONDITION_INVALID", pointer + "/size");
            } else if (condition instanceof VisitedSceneCondition value) {
                Scene referenced = scenes.get(value.scene());
                if (referenced == null) issues.add("BLUEPRINT_SCENE_UNKNOWN", pointer + "/scene");
                else if (choiceRequirement && referenced instanceof EndingScene) issues.add("BLUEPRINT_CONDITION_INVALID", pointer + "/scene");
            } else if (condition instanceof CompositeCondition value) {
                if (value.conditions().isEmpty()) issues.add("BLUEPRINT_CONDITION_INVALID", pointer + "/conditions");
                for (int i = 0; i < value.conditions().size(); i++)
                    inspectCondition(value.conditions().get(i), pointer + "/conditions/" + i, choiceRequirement, depth + 1, nodes);
            } else if (condition instanceof NotCondition value) {
                inspectCondition(value.condition(), pointer + "/condition", choiceRequirement, depth + 1, nodes);
            }
        }

        private void inspectEffect(Effect effect, String pointer) {
            String type = effectType(effect);
            if (type == null || !limits.allowedEffects().contains(type) || !challenge.allowedEffects().contains(type)
                    || !capabilities.allowedEffects().contains(type)) issues.add("BLUEPRINT_EFFECT_INVALID", pointer);
            if (effect instanceof CounterEffect value) {
                Counter declaration = counters.get(value.counter());
                if (declaration == null) issues.add("BLUEPRINT_STATE_UNKNOWN", pointer + "/counter");
                else if ("COUNTER_ADD".equals(value.type()) && (value.value() == 0
                        || Math.abs((long) value.value()) > capabilities.maxCounterDelta()))
                    issues.add("BLUEPRINT_EFFECT_INVALID", pointer + "/delta");
                else if ("COUNTER_SET".equals(value.type())
                        && (value.value() < declaration.min() || value.value() > declaration.max()))
                    issues.add("BLUEPRINT_EFFECT_INVALID", pointer + "/value");
            } else if (effect instanceof FlagEffect value) {
                if (!flags.containsKey(value.flag())) issues.add("BLUEPRINT_STATE_UNKNOWN", pointer + "/flag");
            } else if (effect instanceof SetEffect value) {
                FiniteSet declaration = sets.get(value.set());
                if (declaration == null) issues.add("BLUEPRINT_STATE_UNKNOWN", pointer + "/set");
                else if (!declaration.values().contains(value.value())) issues.add("BLUEPRINT_SET_VALUE_UNKNOWN", pointer + "/value");
            }
        }

        private void staticGraph() {
            for (Scene scene : blueprint.scenes()) {
                List<String> next = new ArrayList<>();
                if (scene instanceof DecisionScene decision) for (Choice choice : decision.choices()) {
                    if (scenes.containsKey(choice.destinationSceneId())) next.add(choice.destinationSceneId());
                }
                graph.put(sceneId(scene), List.copyOf(next)); edgeCount += next.size();
            }
            Scene start = scenes.get(blueprint.startSceneId());
            if (!(start instanceof DecisionScene) || scenePhase(start) != PhaseKind.OPENING)
                issues.add("BLUEPRINT_START_INVALID", "/startSceneId");
            for (int i = 0; i < blueprint.scenes().size(); i++) {
                Scene source = blueprint.scenes().get(i); int from = phaseOrdinal(scenePhase(source));
                if (source instanceof DecisionScene decision) for (int j = 0; j < decision.choices().size(); j++) {
                    Scene destination = scenes.get(decision.choices().get(j).destinationSceneId());
                    if (destination == null) continue; int to = phaseOrdinal(scenePhase(destination));
                    if (to < from) issues.add("BLUEPRINT_ROUTE_BACKWARDS", "/scenes/" + i + "/choices/" + j + "/destinationSceneId");
                    else if (to > from + 1) issues.add("BLUEPRINT_ROUTE_SKIPS_PHASE", "/scenes/" + i + "/choices/" + j + "/destinationSceneId");
                }
            }
            if (hasCycle()) issues.add("BLUEPRINT_ROUTE_CYCLIC", "/scenes");
            if (!(start instanceof DecisionScene) || !issues.empty()) return;
            structurallyReachable = reachable(blueprint.startSceneId());
            for (int i = 0; i < blueprint.scenes().size(); i++) if (!structurallyReachable.contains(sceneId(blueprint.scenes().get(i))))
                issues.add("BLUEPRINT_SCENE_UNREACHABLE", "/scenes/" + i);
            Set<String> canReachEnding = reverseReachable(endingIds);
            for (int i = 0; i < blueprint.scenes().size(); i++) {
                String id = sceneId(blueprint.scenes().get(i));
                if (structurallyReachable.contains(id) && !canReachEnding.contains(id)) issues.add("BLUEPRINT_ROUTE_DEAD_END", "/scenes/" + i);
            }
            validateStructuralLengths();
        }

        private void validateStructuralLengths() {
            List<String> order = topologicalOrder();
            if (order.size() != scenes.size()) return;
            Map<String, Set<Integer>> lengths = new LinkedHashMap<>();
            for (String id : scenes.keySet()) lengths.put(id, new LinkedHashSet<>());
            lengths.get(blueprint.startSceneId()).add(0);
            for (String id : order) for (String next : graph.getOrDefault(id, List.of()))
                for (int length : lengths.get(id)) lengths.get(next).add(length + 1);
            boolean any = false;
            for (int i = 0; i < blueprint.scenes().size(); i++) if (blueprint.scenes().get(i) instanceof EndingScene ending) {
                boolean inRange = lengths.get(ending.id()).stream().anyMatch(this::inLengthRange);
                if (!inRange) issues.add("BLUEPRINT_LENGTH_INVALID", "/scenes/" + i);
                any |= inRange;
            }
            if (!any) issues.add("BLUEPRINT_LENGTH_INVALID", "/scenes");
        }

        private void coverage() {
            Set<String> phaseActors = new LinkedHashSet<>(), phaseObjectives = new LinkedHashSet<>();
            for (Phase phase : blueprint.phases()) { phaseActors.addAll(phase.actorIds()); phaseObjectives.addAll(phase.objectiveIds()); }
            Set<String> sceneActors = new LinkedHashSet<>(), sceneObjectives = new LinkedHashSet<>();
            for (String id : structurallyReachable) {
                Scene scene = scenes.get(id); sceneActors.addAll(sceneActors(scene)); sceneObjectives.addAll(sceneObjectives(scene));
            }
            for (int i = 0; i < blueprint.actors().size(); i++) {
                String id = blueprint.actors().get(i).id();
                if (!phaseActors.contains(id) || !sceneActors.contains(id)) issues.add("BLUEPRINT_ACTOR_UNUSED", "/actors/" + i);
            }
            for (int i = 0; i < blueprint.objectives().size(); i++) {
                String id = blueprint.objectives().get(i).id();
                if (!phaseObjectives.contains(id) || !sceneObjectives.contains(id)) issues.add("BLUEPRINT_OBJECTIVE_UNPLANNED", "/objectives/" + i);
            }
        }

        private Validation explore() {
            StateBindings bindings = new StateBindings();
            Config initial = bindings.initial();
            initial = withCompleted(initial, completedObjectives(initial));
            List<Config> configurations = new ArrayList<>(); configurations.add(initial);
            Map<Config, Integer> indexes = new LinkedHashMap<>(); indexes.put(initial, 0);
            List<Set<Integer>> next = new ArrayList<>(); next.add(new LinkedHashSet<>());
            Deque<Integer> work = new ArrayDeque<>(); work.add(0);
            Set<String> witnessedEndings = new LinkedHashSet<>();
            int minimum = Integer.MAX_VALUE, maximum = Integer.MIN_VALUE;
            boolean meaningful = false;
            while (!work.isEmpty()) {
                int currentIndex = work.removeFirst(); Config current = configurations.get(currentIndex);
                Scene scene = scenes.get(current.sceneId());
                if (scene instanceof EndingScene ending) {
                    witnessedEndings.add(ending.id()); minimum = Math.min(minimum, current.transitions());
                    maximum = Math.max(maximum, current.transitions());
                    if (!inLengthRange(current.transitions())) issues.add("BLUEPRINT_LENGTH_INVALID", "/scenes/" + sceneIndexes.get(ending.id()));
                    continue;
                }
                DecisionScene decision = (DecisionScene) scene;
                int sceneIndex = sceneIndexes.get(decision.id());
                List<Choice> enabled = new ArrayList<>();
                for (int i = 0; i < decision.choices().size(); i++) {
                    var mechanic = mechanicsPlan.sceneChoices().get(sceneIndex).get(i);
                    if (evaluate(mechanic.requires(), current, bindings)) enabled.add(decision.choices().get(i));
                }
                if (enabled.isEmpty()) issues.add("BLUEPRINT_ROUTE_DEAD_END", "/scenes/" + sceneIndexes.get(decision.id()));
                if (decision.phase().ordinal() < PhaseKind.RESOLUTION.ordinal()
                        && enabled.stream().map(TransitionSignature::new).distinct().count() >= 2) meaningful = true;
                for (Choice choice : enabled) {
                    int choiceIndex = decision.choices().indexOf(choice);
                    Config target = apply(choice, mechanicsPlan.sceneChoices().get(sceneIndex).get(choiceIndex), current, bindings);
                    if (target == null) continue;
                    Integer targetIndex = indexes.get(target);
                    if (targetIndex == null) {
                        if (configurations.size() >= capabilities.maxExploredStates()) {
                            return new Validation(Status.INCONCLUSIVE, VALIDATOR_VERSION,
                                    List.of(issue("BLUEPRINT_STATE_LIMIT_EXCEEDED", "/scenes")), null);
                        }
                        targetIndex = configurations.size(); indexes.put(target, targetIndex); configurations.add(target);
                        next.add(new LinkedHashSet<>()); work.addLast(targetIndex);
                    }
                    next.get(currentIndex).add(targetIndex);
                }
            }
            if (!meaningful) issues.add("BLUEPRINT_CHOICE_REQUIRED", "/scenes");
            for (int i = 0; i < blueprint.scenes().size(); i++) if (blueprint.scenes().get(i) instanceof EndingScene ending
                    && !witnessedEndings.contains(ending.id())) issues.add("BLUEPRINT_ENDING_UNREACHABLE", "/scenes/" + i);
            Set<Integer> completable = completableConfigurations(configurations, next);
            List<String> witnessedObjectives = new ArrayList<>();
            for (int i = 0; i < blueprint.objectives().size(); i++) {
                String id = blueprint.objectives().get(i).id();
                boolean witnessed = completable.stream().anyMatch(index -> configurations.get(index).completedObjectives().contains(id));
                if (witnessed) witnessedObjectives.add(id); else issues.add("BLUEPRINT_OBJECTIVE_UNACHIEVABLE", "/objectives/" + i);
            }
            if (!issues.empty()) return reject();
            List<String> orderedEndings = blueprint.scenes().stream().filter(EndingScene.class::isInstance)
                    .map(EndingScene.class::cast).map(EndingScene::id).filter(witnessedEndings::contains).toList();
            SemanticallyValidatedBlueprint.Evidence evidence = new SemanticallyValidatedBlueprint.Evidence(
                    CONTRACT_VERSION, VALIDATOR_VERSION, scenes.size(), edgeCount, configurations.size(), minimum, maximum,
                    orderedEndings, witnessedObjectives);
            SemanticallyValidatedBlueprint accepted = new SemanticallyValidatedBlueprint(blueprint,
                    validatedProfile.identity(), preferences.gameLocale(), preferences.lengthPresetId(),
                    preferences.challengePresetId(), evidence, mechanicsPlan);
            return new Validation(Status.PASS, VALIDATOR_VERSION, List.of(), accepted);
        }

        private Set<Integer> completableConfigurations(List<Config> configurations, List<Set<Integer>> next) {
            List<Set<Integer>> reverse = new ArrayList<>();
            for (int i = 0; i < configurations.size(); i++) reverse.add(new LinkedHashSet<>());
            for (int i = 0; i < next.size(); i++) for (int target : next.get(i)) reverse.get(target).add(i);
            Set<Integer> result = new LinkedHashSet<>(); Deque<Integer> todo = new ArrayDeque<>();
            for (int i = 0; i < configurations.size(); i++) if (scenes.get(configurations.get(i).sceneId()) instanceof EndingScene) todo.add(i);
            while (!todo.isEmpty()) { int value = todo.removeFirst(); if (result.add(value)) todo.addAll(reverse.get(value)); }
            return result;
        }

        private Config apply(Choice choice, ValidatedMechanicsPlan.ChoiceMechanics mechanics,
                             Config source, StateBindings bindings) {
            List<Integer> counterValues = new ArrayList<>(source.counters());
            List<Boolean> flagValues = new ArrayList<>(source.flags());
            List<Set<String>> setValues = new ArrayList<>(); source.sets().forEach(value -> setValues.add(new LinkedHashSet<>(value)));
            StateEffectSemantics.StateMutation mutation = bindings.mutation(counterValues, flagValues, setValues);
            for (com.capybee.engine.rules.Effect effect : mechanics.effects()) {
                StateEffectSemantics.Failure failure = StateEffectSemantics.apply(effect, mutation,
                        StateEffectSemantics.CounterPolicy.REJECT_OUT_OF_RANGE);
                if (failure == StateEffectSemantics.Failure.COUNTER_OUT_OF_RANGE) {
                    issues.add("BLUEPRINT_STATE_BOUNDS_VIOLATED", "/scenes/" + sceneIndexes.get(source.sceneId()));
                    return null;
                }
                if (failure != StateEffectSemantics.Failure.NONE) {
                    issues.add("BLUEPRINT_MECHANICS_CONTRACT_MISMATCH", "");
                    return null;
                }
            }
            Set<String> visited = new LinkedHashSet<>(source.visitedScenes()); visited.add(choice.destinationSceneId());
            Config moved = new Config(choice.destinationSceneId(), counterValues, flagValues, immutableSets(setValues),
                    visited, source.completedObjectives(), source.transitions() + 1);
            Set<String> completed = new LinkedHashSet<>(source.completedObjectives()); completed.addAll(completedObjectives(moved));
            return withCompleted(moved, completed);
        }

        private Set<String> completedObjectives(Config config) {
            Set<String> completed = new LinkedHashSet<>(); StateBindings bindings = new StateBindings();
            for (int i = 0; i < blueprint.objectives().size(); i++)
                if (evaluate(mechanicsPlan.objectiveConditions().get(i), config, bindings))
                    completed.add(blueprint.objectives().get(i).id());
            return completed;
        }

        private boolean evaluate(com.capybee.engine.rules.Condition condition, Config state, StateBindings bindings) {
            if (condition == null) return true;
            return ConditionEvaluator.evaluate(condition, bindings.view(state));
        }

        private Set<String> reachable(String start) {
            Set<String> result = new LinkedHashSet<>(); Deque<String> todo = new ArrayDeque<>(); todo.add(start);
            while (!todo.isEmpty()) { String id = todo.removeFirst(); if (result.add(id)) todo.addAll(graph.getOrDefault(id, List.of())); }
            return result;
        }

        private Set<String> reverseReachable(Set<String> starts) {
            Map<String, List<String>> reverse = new LinkedHashMap<>(); scenes.keySet().forEach(id -> reverse.put(id, new ArrayList<>()));
            graph.forEach((from, targets) -> targets.forEach(to -> reverse.get(to).add(from)));
            Set<String> result = new LinkedHashSet<>(); Deque<String> todo = new ArrayDeque<>(starts);
            while (!todo.isEmpty()) { String id = todo.removeFirst(); if (result.add(id)) todo.addAll(reverse.getOrDefault(id, List.of())); }
            return result;
        }

        private boolean hasCycle() { return topologicalOrder().size() != scenes.size(); }

        private List<String> topologicalOrder() {
            Map<String, Integer> degree = new LinkedHashMap<>(); scenes.keySet().forEach(id -> degree.put(id, 0));
            graph.values().forEach(targets -> targets.forEach(target -> degree.computeIfPresent(target, (key, value) -> value + 1)));
            Deque<String> todo = new ArrayDeque<>(); degree.forEach((id, value) -> { if (value == 0) todo.add(id); });
            List<String> result = new ArrayList<>();
            while (!todo.isEmpty()) { String id = todo.removeFirst(); result.add(id); for (String target : graph.getOrDefault(id, List.of())) {
                int value = degree.computeIfPresent(target, (key, old) -> old - 1); if (value == 0) todo.addLast(target);
            }}
            return result;
        }

        private boolean inLengthRange(int value) { return value >= limits.minStoryTransitions() && value <= limits.maxStoryTransitions(); }
        private void range(int value, int minimum, int maximum, String pointer) {
            if (value < minimum || value > maximum) issues.add("BLUEPRINT_CAPABILITY_VIOLATION", pointer);
        }
        private Validation reject() { return new Validation(Status.REJECT, VALIDATOR_VERSION, issues.values(), null); }

        private final class StateBindings {
            final Map<String, Integer> counterIndex = indexes(counters.keySet());
            final Map<String, Integer> flagIndex = indexes(flags.keySet());
            final Map<String, Integer> setIndex = indexes(sets.keySet());
            Config initial() {
                return new Config(blueprint.startSceneId(), counters.values().stream().map(Counter::initial).toList(),
                        flags.values().stream().map(Flag::initial).toList(),
                        sets.values().stream().map(v -> Set.copyOf(v.initial())).toList(),
                        Set.of(blueprint.startSceneId()), Set.of(), 0);
            }

            RuleStateView view(Config state) {
                return new RuleStateView() {
                    public int counter(String id) { return state.counters().get(required(counterIndex, id)); }
                    public boolean flag(String id) { return state.flags().get(required(flagIndex, id)); }
                    public Set<String> set(String id) { return state.sets().get(required(setIndex, id)); }
                    public boolean visitedScene(String id) { return state.visitedScenes().contains(id); }
                    public long step() { return state.transitions(); }
                    public boolean atPlace(String id) { return false; }
                    public boolean visitedPlace(String id) { return false; }
                };
            }

            StateEffectSemantics.StateMutation mutation(List<Integer> counterValues, List<Boolean> flagValues,
                                                         List<Set<String>> setValues) {
                return new StateEffectSemantics.StateMutation() {
                    public Integer counterValue(String id) { Integer i=counterIndex.get(id); return i==null?null:counterValues.get(i); }
                    public Integer counterMin(String id) { Counter v=counters.get(id); return v==null?null:v.min(); }
                    public Integer counterMax(String id) { Counter v=counters.get(id); return v==null?null:v.max(); }
                    public Boolean flagValue(String id) { Integer i=flagIndex.get(id); return i==null?null:flagValues.get(i); }
                    public boolean hasSet(String id) { return setIndex.containsKey(id); }
                    public boolean isSetMember(String id,String value) { FiniteSet v=sets.get(id); return v!=null&&v.values().contains(value); }
                    public void setCounter(String id,int value) { counterValues.set(required(counterIndex,id),value); }
                    public void setFlag(String id,boolean value) { flagValues.set(required(flagIndex,id),value); }
                    public void addSetValue(String id,String value) { setValues.get(required(setIndex,id)).add(value); }
                    public void removeSetValue(String id,String value) { setValues.get(required(setIndex,id)).remove(value); }
                };
            }
        }
    }

    private static Map<String, Integer> indexes(Collection<String> values) {
        Map<String, Integer> result = new LinkedHashMap<>(); int index = 0;
        for (String value : values) result.put(value, index++); return result;
    }
    private static int required(Map<String, Integer> values, String id) {
        Integer result = values.get(id);
        if (result == null) throw new IllegalStateException("Generated mechanics binding invariant failed");
        return result;
    }
    private static List<Set<String>> immutableSets(List<Set<String>> values) { return values.stream().map(Set::copyOf).toList(); }
    private static Config withCompleted(Config value, Set<String> completed) {
        return new Config(value.sceneId(), value.counters(), value.flags(), value.sets(), value.visitedScenes(), completed, value.transitions());
    }
    private static String sceneId(Scene scene) { return scene instanceof DecisionScene value ? value.id() : ((EndingScene) scene).id(); }
    private static PhaseKind scenePhase(Scene scene) { return scene instanceof DecisionScene value ? value.phase() : ((EndingScene) scene).phase(); }
    private static int phaseOrdinal(PhaseKind phase) { return phase == null ? Integer.MAX_VALUE / 2 : phase.ordinal(); }
    private static List<String> sceneActors(Scene scene) { return scene instanceof DecisionScene value ? value.actorIds() : ((EndingScene) scene).actorIds(); }
    private static List<String> sceneObjectives(Scene scene) { return scene instanceof DecisionScene value ? value.objectiveIds() : ((EndingScene) scene).objectiveIds(); }
    private static String conditionType(Condition value) {
        if (value instanceof CounterCondition v) return conditionName(v.type());
        if (value instanceof FlagCondition) return "FLAG_IS";
        if (value instanceof SetContainsCondition) return "SET_CONTAINS";
        if (value instanceof SetSizeCondition) return "SET_SIZE_AT_LEAST";
        if (value instanceof VisitedSceneCondition) return "VISITED_SCENE";
        if (value instanceof CompositeCondition v) return conditionName(v.type());
        return value instanceof NotCondition ? "NOT" : null;
    }
    private static String effectType(Effect value) {
        if (value instanceof CounterEffect v) return effectName(v.type());
        if (value instanceof FlagEffect) return "FLAG_SET";
        if (value instanceof SetEffect v) return effectName(v.type());
        return null;
    }
    private static String conditionName(String value) {
        GeneratedMechanicsV1.Operator operator=GeneratedMechanicsV1.operator(value);
        return operator!=null&&operator.category()==GeneratedMechanicsV1.Category.CONDITION?operator.name():null;
    }
    private static String effectName(String value) {
        GeneratedMechanicsV1.Operator operator=GeneratedMechanicsV1.operator(value);
        return operator!=null&&operator.category()==GeneratedMechanicsV1.Category.EFFECT?operator.name():null;
    }

    private record Config(String sceneId, List<Integer> counters, List<Boolean> flags, List<Set<String>> sets,
                          Set<String> visitedScenes, Set<String> completedObjectives, int transitions) {
        Config { counters = List.copyOf(counters); flags = List.copyOf(flags); sets = immutableSets(sets);
            visitedScenes = Set.copyOf(visitedScenes); completedObjectives = Set.copyOf(completedObjectives); }
    }
    private record TransitionSignature(String destination, List<Effect> effects) {
        TransitionSignature(Choice choice) { this(choice.destinationSceneId(), choice.effects()); }
        TransitionSignature { effects = List.copyOf(effects); }
    }
    private static final class Issues {
        private static final int LIMIT = 100;
        private final List<BlueprintIssue> values = new ArrayList<>();
        private final Set<String> keys = new LinkedHashSet<>();
        void add(String code, String pointer) {
            String key = code + '\0' + pointer; if (!keys.add(key)) return;
            if (values.size() < LIMIT - 1) values.add(issue(code, pointer));
            else if (values.size() == LIMIT - 1) values.add(issue("BLUEPRINT_ISSUES_TRUNCATED", ""));
        }
        boolean empty() { return values.isEmpty(); }
        List<BlueprintIssue> values() { return List.copyOf(values); }
    }
}
