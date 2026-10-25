package com.capybee.engine.generation.blueprint;

import com.capybee.engine.content.Scene;
import com.capybee.engine.content.SceneOption;
import com.capybee.engine.content.SceneSet;
import com.capybee.engine.generation.PhaseKind;
import com.capybee.engine.rules.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.capybee.engine.generation.blueprint.MaterializedGameCompilationResult.*;

/** Pure deterministic compiler from the reviewed trust boundary to runtime-shaped engine data. */
public final class MaterializedGameCompiler {
    public static final String COMPILER_VERSION = "materialized-game-compiler-v1";
    public static final String UNRESOLVED_LABEL = "__materialized_unresolved_label__";

    private final MaterializedGameDefinitionVerifier verifier;

    public MaterializedGameCompiler() { this(new MaterializedGameDefinitionVerifier()); }

    MaterializedGameCompiler(MaterializedGameDefinitionVerifier verifier) { this.verifier = verifier; }

    public MaterializedGameCompilationResult compile(BlueprintGenerationResult.CandidateReviewed candidate) {
        String invalid = validateTrust(candidate);
        if (invalid != null) return new InvalidAcceptedInput(invalid);
        try {
            MaterializedGameDefinition definition = materialize(candidate);
            MaterializedGameDefinitionVerifier.Verification verification = verifier.verify(definition);
            return verification.valid() ? new Compiled(definition) : new CompilerFailure(verification.code());
        } catch (RuntimeException ex) {
            return new CompilerFailure("MATERIALIZED_DEFINITION_COMPILER_FAILURE");
        }
    }

    private String validateTrust(BlueprintGenerationResult.CandidateReviewed candidate) {
        if (candidate == null || candidate.blueprint() == null || candidate.evidence() == null)
            return "MATERIALIZED_ACCEPTED_INPUT_MISSING";
        SemanticallyValidatedBlueprint accepted = candidate.blueprint();
        BlueprintGenerationResult.Evidence review = candidate.evidence();
        if (accepted.blueprint() == null || accepted.mechanicsPlan() == null || accepted.evidence() == null)
            return "MATERIALIZED_ACCEPTED_INPUT_MISSING";
        if (!accepted.profileIdentity().equals(review.profile()))
            return "MATERIALIZED_ACCEPTED_INPUT_PROFILE_MISMATCH";
        if (blank(review.reviewerVersion()) || blank(review.validatorVersion()) || blank(review.preparationId())
                || review.attempts() < 1)
            return "MATERIALIZED_ACCEPTED_INPUT_STAMP_INVALID";
        if (!review.validatorVersion().equals(accepted.evidence().validatorVersion()))
            return "MATERIALIZED_ACCEPTED_INPUT_VALIDATOR_MISMATCH";
        if (!GeneratedMechanicsV1.CONTRACT_VERSION.equals(accepted.evidence().semanticContractVersion())
                || !GeneratedMechanicsV1.CONTRACT_VERSION.equals(accepted.mechanicsPlan().contractVersion()))
            return "MATERIALIZED_ACCEPTED_INPUT_CONTRACT_MISMATCH";
        ParsedGameBlueprint source = accepted.blueprint();
        if (!accepted.locale().value().equals(source.locale()))
            return "MATERIALIZED_ACCEPTED_INPUT_LOCALE_MISMATCH";
        List<String> endingIds = source.scenes().stream().filter(ParsedGameBlueprint.EndingScene.class::isInstance)
                .map(ParsedGameBlueprint.EndingScene.class::cast).map(ParsedGameBlueprint.EndingScene::id).toList();
        List<String> objectiveIds = source.objectives().stream().map(ParsedGameBlueprint.Objective::id).toList();
        if (!endingIds.equals(accepted.evidence().witnessedEndingIds())
                || !objectiveIds.equals(accepted.evidence().witnessedObjectiveIds()))
            return "MATERIALIZED_ACCEPTED_INPUT_WITNESS_MISMATCH";
        long edges = source.scenes().stream().filter(ParsedGameBlueprint.DecisionScene.class::isInstance)
                .map(ParsedGameBlueprint.DecisionScene.class::cast).mapToLong(scene -> scene.choices().size()).sum();
        if (source.scenes().size() != accepted.evidence().sceneCount() || edges != accepted.evidence().edgeCount())
            return "MATERIALIZED_ACCEPTED_INPUT_EVIDENCE_MISMATCH";
        if (accepted.mechanicsPlan().objectiveConditions().size() != source.objectives().size()
                || accepted.mechanicsPlan().sceneChoices().size() != source.scenes().size())
            return "MATERIALIZED_ACCEPTED_INPUT_MECHANICS_MISMATCH";
        for (int i = 0; i < source.scenes().size(); i++) {
            int expected = source.scenes().get(i) instanceof ParsedGameBlueprint.DecisionScene decision
                    ? decision.choices().size() : 0;
            if (accepted.mechanicsPlan().sceneChoices().get(i).size() != expected)
                return "MATERIALIZED_ACCEPTED_INPUT_MECHANICS_MISMATCH";
        }
        return null;
    }

    private MaterializedGameDefinition materialize(BlueprintGenerationResult.CandidateReviewed candidate) {
        SemanticallyValidatedBlueprint accepted = candidate.blueprint();
        ParsedGameBlueprint source = accepted.blueprint();
        ValidatedMechanicsPlan mechanics = accepted.mechanicsPlan();
        Bindings bindings = Bindings.create(source);

        List<MaterializedGameDefinition.ActorPlan> actors = new ArrayList<>();
        List<MaterializedGameDefinition.PhasePlan> phases = new ArrayList<>();
        List<MaterializedGameDefinition.ScenePlan> scenePlans = new ArrayList<>();
        List<Scene> runtimeScenes = new ArrayList<>();
        LinkedHashMap<String, String> content = new LinkedHashMap<>();
        content.put("story.title", source.title());

        for (int i = 0; i < source.actors().size(); i++) {
            ParsedGameBlueprint.Actor actor = source.actors().get(i);
            String id = bindings.actors.get(actor.id());
            actors.add(new MaterializedGameDefinition.ActorPlan(id, actor.name(), actor.role(), actor.description()));
            content.put(key("actor", i, "name"), actor.name());
        }
        for (int i = 0; i < source.objectives().size(); i++) {
            ParsedGameBlueprint.Objective objective = source.objectives().get(i);
            content.put(key("objective", i, "title"), objective.title());
            content.put(key("objective", i, "description"), objective.description());
        }
        for (int i = 0; i < source.outcomes().size(); i++) {
            ParsedGameBlueprint.Outcome outcome = source.outcomes().get(i);
            content.put(key("outcome", i, "title"), outcome.title());
            content.put(key("outcome", i, "description"), outcome.description());
        }

        int choiceCount = 0;
        for (int sceneIndex = 0; sceneIndex < source.scenes().size(); sceneIndex++) {
            ParsedGameBlueprint.Scene authored = source.scenes().get(sceneIndex);
            String sourceSceneId = sceneId(authored);
            String id = bindings.scenes.get(sourceSceneId);
            PhaseKind phase = scenePhase(authored);
            List<String> actorIds = mapList(sceneActors(authored), bindings.actors);
            List<String> objectiveIds = mapList(sceneObjectives(authored), bindings.objectives);
            content.put(key("scene", sceneIndex, "title"), sceneTitle(authored));
            List<MaterializedGameDefinition.ChoicePlan> choices = new ArrayList<>();
            if (authored instanceof ParsedGameBlueprint.DecisionScene decision) {
                List<SceneOption> options = new ArrayList<>();
                for (int choiceIndex = 0; choiceIndex < decision.choices().size(); choiceIndex++) {
                    ParsedGameBlueprint.Choice choice = decision.choices().get(choiceIndex);
                    String choiceId = bindings.choices.get(sourceSceneId).get(choice.id());
                    ValidatedMechanicsPlan.ChoiceMechanics typed = mechanics.sceneChoices().get(sceneIndex).get(choiceIndex);
                    choices.add(new MaterializedGameDefinition.ChoicePlan(choiceId, choice.intent()));
                    content.put(choiceKey(sceneIndex, choiceIndex), choice.intent());
                    options.add(new SceneOption(choiceId, UNRESOLVED_LABEL,
                            required(bindings.scenes, choice.destinationSceneId()),
                            rewriteEffects(typed.effects(), bindings), rewriteCondition(typed.requires(), bindings),
                            null, false));
                    choiceCount++;
                }
                runtimeScenes.add(new Scene(id, source.startSceneId().equals(sourceSceneId), List.of(), options,
                        null, false, List.of(), List.of(), null, null, 0));
            } else {
                ParsedGameBlueprint.EndingScene ending = (ParsedGameBlueprint.EndingScene) authored;
                runtimeScenes.add(new Scene(id, false, List.of(), List.of(), null, true,
                        List.of(), List.of(), required(bindings.outcomes, ending.outcomeId()), null, 0));
            }
            scenePlans.add(new MaterializedGameDefinition.ScenePlan(id, phase, sceneTitle(authored), sceneIntent(authored),
                    actorIds, objectiveIds, choices));
        }

        for (ParsedGameBlueprint.Phase phase : source.phases()) {
            List<String> phaseScenes = source.scenes().stream().filter(scene -> scenePhase(scene) == phase.kind())
                    .map(scene -> bindings.scenes.get(sceneId(scene))).toList();
            phases.add(new MaterializedGameDefinition.PhasePlan("phase-" + phase.kind().name().toLowerCase(Locale.ROOT), phase.kind(),
                    phase.goal(), phase.development(), phase.transition(), mapList(phase.actorIds(), bindings.actors),
                    mapList(phase.objectiveIds(), bindings.objectives), phaseScenes));
        }

        Rules rules = materializeRules(source, mechanics, bindings);
        LinkedHashMap<String, Scene> sceneMap = new LinkedHashMap<>();
        runtimeScenes.forEach(scene -> sceneMap.put(scene.id(), scene));
        SceneSet sceneSet = new SceneSet(accepted.locale(), sceneMap, required(bindings.scenes, source.startSceneId()));
        SemanticallyValidatedBlueprint.Evidence semantic = accepted.evidence();
        MaterializedGameDefinition.CompilationEvidence evidence = new MaterializedGameDefinition.CompilationEvidence(
                actors.size(), source.objectives().size(), runtimeScenes.size(), choiceCount, source.outcomes().size(),
                source.state().counters().size(), source.state().flags().size(), source.state().sets().size(),
                semantic.edgeCount(), semantic.exploredConfigurationCount(), semantic.minimumTransitions(), semantic.maximumTransitions());
        return new MaterializedGameDefinition(MaterializedGameDefinition.FORMAT_VERSION, COMPILER_VERSION,
                MaterializedGameDefinition.Origin.DYNAMIC_BLUEPRINT, accepted.profileIdentity(), accepted.locale(),
                accepted.lengthPresetId(), accepted.challengePresetId(), source.schemaVersion(),
                GeneratedMechanicsV1.CONTRACT_VERSION, semantic.validatorVersion(), candidate.evidence().reviewerVersion(),
                new MaterializedGameDefinition.StoryPlan(source.title(), source.premise(), source.setting(),
                        source.toneTags(), source.storyPromises()), actors, phases, scenePlans, runtimeScenes, sceneSet,
                rules, content, bindings.publicBindings(), evidence);
    }

    private Rules materializeRules(ParsedGameBlueprint source, ValidatedMechanicsPlan mechanics, Bindings bindings) {
        Map<String, Integer> maxDeltas = new LinkedHashMap<>();
        mechanics.sceneChoices().stream().flatMap(List::stream).flatMap(choice -> choice.effects().stream())
                .filter(Effect.CounterAdd.class::isInstance).map(Effect.CounterAdd.class::cast)
                .forEach(effect -> maxDeltas.merge(effect.counter(), Math.abs(effect.delta()), Math::max));
        List<CounterDef> counters = source.state().counters().stream().map(counter -> new CounterDef(
                required(bindings.counters, counter.id()), "state." + required(bindings.counters, counter.id()),
                counter.min(), counter.max(), counter.initial(), false, maxDeltas.get(counter.id()))).toList();
        List<FlagDef> flags = source.state().flags().stream().map(flag ->
                new FlagDef(required(bindings.flags, flag.id()), flag.initial())).toList();
        List<SetDef> sets = source.state().sets().stream().map(set -> new SetDef(required(bindings.sets, set.id()),
                new ArrayList<>(bindings.setMembers.get(set.id()).values()), mapList(set.initial(), bindings.setMembers.get(set.id())))).toList();
        List<ObjectiveDef> objectives = new ArrayList<>();
        for (int i = 0; i < source.objectives().size(); i++) {
            String id = required(bindings.objectives, source.objectives().get(i).id());
            objectives.add(new ObjectiveDef(id, key("objective", i, "title"),
                    rewriteCondition(mechanics.objectiveConditions().get(i), bindings), List.of()));
        }
        List<OutcomeDef> outcomes = new ArrayList<>();
        for (int i = 0; i < source.outcomes().size(); i++) {
            ParsedGameBlueprint.Outcome outcome = source.outcomes().get(i);
            outcomes.add(new OutcomeDef(required(bindings.outcomes, outcome.id()), outcome.resultClass(), key("outcome", i, "title")));
        }
        return new Rules(counters, flags, sets, objectives, outcomes, List.of());
    }

    private Condition rewriteCondition(Condition value, Bindings b) {
        if (value == null) return null;
        if (value instanceof Condition.CounterAtLeast v) return new Condition.CounterAtLeast(required(b.counters, v.counter()), v.value());
        if (value instanceof Condition.CounterAtMost v) return new Condition.CounterAtMost(required(b.counters, v.counter()), v.value());
        if (value instanceof Condition.CounterEquals v) return new Condition.CounterEquals(required(b.counters, v.counter()), v.value());
        if (value instanceof Condition.FlagIs v) return new Condition.FlagIs(required(b.flags, v.flag()), v.value());
        if (value instanceof Condition.SetContains v) return new Condition.SetContains(required(b.sets, v.set()), member(b, v.set(), v.value()));
        if (value instanceof Condition.SetSizeAtLeast v) return new Condition.SetSizeAtLeast(required(b.sets, v.set()), v.size());
        if (value instanceof Condition.VisitedScene v) return new Condition.VisitedScene(required(b.scenes, v.scene()));
        if (value instanceof Condition.All v) return new Condition.All(v.conditions().stream().map(c -> rewriteCondition(c, b)).toList());
        if (value instanceof Condition.Any v) return new Condition.Any(v.conditions().stream().map(c -> rewriteCondition(c, b)).toList());
        if (value instanceof Condition.Not v) return new Condition.Not(rewriteCondition(v.condition(), b));
        throw new IllegalArgumentException("Unsupported generated condition");
    }

    private List<Effect> rewriteEffects(List<Effect> values, Bindings b) {
        return values.stream().map(value -> rewriteEffect(value, b)).toList();
    }

    private Effect rewriteEffect(Effect value, Bindings b) {
        if (value instanceof Effect.CounterAdd v) return new Effect.CounterAdd(required(b.counters, v.counter()), v.delta());
        if (value instanceof Effect.CounterSet v) return new Effect.CounterSet(required(b.counters, v.counter()), v.value());
        if (value instanceof Effect.FlagSet v) return new Effect.FlagSet(required(b.flags, v.flag()), v.value());
        if (value instanceof Effect.SetAdd v) return new Effect.SetAdd(required(b.sets, v.set()), member(b, v.set(), v.value()));
        if (value instanceof Effect.SetRemove v) return new Effect.SetRemove(required(b.sets, v.set()), member(b, v.set(), v.value()));
        throw new IllegalArgumentException("Unsupported generated effect");
    }

    private static String member(Bindings b, String set, String value) { return required(b.setMembers.get(set), value); }
    private static String required(Map<String, String> map, String key) {
        String value = map == null ? null : map.get(key);
        if (value == null) throw new IllegalArgumentException("Unresolved reference");
        return value;
    }
    private static List<String> mapList(List<String> values, Map<String, String> map) {
        return values.stream().map(value -> required(map, value)).toList();
    }
    private static String key(String kind, int index, String field) { return kind + "." + pad(index + 1) + "." + field; }
    private static String choiceKey(int scene, int choice) { return "scene." + pad(scene + 1) + ".choice." + pad(choice + 1) + ".intent"; }
    private static String pad(int value) { return "%03d".formatted(value); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String sceneId(ParsedGameBlueprint.Scene scene) { return scene instanceof ParsedGameBlueprint.DecisionScene v ? v.id() : ((ParsedGameBlueprint.EndingScene) scene).id(); }
    private static PhaseKind scenePhase(ParsedGameBlueprint.Scene scene) { return scene instanceof ParsedGameBlueprint.DecisionScene v ? v.phase() : ((ParsedGameBlueprint.EndingScene) scene).phase(); }
    private static String sceneTitle(ParsedGameBlueprint.Scene scene) { return scene instanceof ParsedGameBlueprint.DecisionScene v ? v.title() : ((ParsedGameBlueprint.EndingScene) scene).title(); }
    private static String sceneIntent(ParsedGameBlueprint.Scene scene) { return scene instanceof ParsedGameBlueprint.DecisionScene v ? v.intent() : ((ParsedGameBlueprint.EndingScene) scene).intent(); }
    private static List<String> sceneActors(ParsedGameBlueprint.Scene scene) { return scene instanceof ParsedGameBlueprint.DecisionScene v ? v.actorIds() : ((ParsedGameBlueprint.EndingScene) scene).actorIds(); }
    private static List<String> sceneObjectives(ParsedGameBlueprint.Scene scene) { return scene instanceof ParsedGameBlueprint.DecisionScene v ? v.objectiveIds() : ((ParsedGameBlueprint.EndingScene) scene).objectiveIds(); }

    private static final class Bindings {
        final LinkedHashMap<String, String> actors = new LinkedHashMap<>(), objectives = new LinkedHashMap<>(),
                scenes = new LinkedHashMap<>(), outcomes = new LinkedHashMap<>(), counters = new LinkedHashMap<>(),
                flags = new LinkedHashMap<>(), sets = new LinkedHashMap<>();
        final LinkedHashMap<String, Map<String, String>> choices = new LinkedHashMap<>(), setMembers = new LinkedHashMap<>();
        static Bindings create(ParsedGameBlueprint source) {
            Bindings b = new Bindings();
            allocate(source.actors().stream().map(ParsedGameBlueprint.Actor::id).toList(), "actor", b.actors);
            allocate(source.objectives().stream().map(ParsedGameBlueprint.Objective::id).toList(), "objective", b.objectives);
            allocate(source.scenes().stream().map(MaterializedGameCompiler::sceneId).toList(), "scene", b.scenes);
            allocate(source.outcomes().stream().map(ParsedGameBlueprint.Outcome::id).toList(), "outcome", b.outcomes);
            allocate(source.state().counters().stream().map(ParsedGameBlueprint.Counter::id).toList(), "counter", b.counters);
            allocate(source.state().flags().stream().map(ParsedGameBlueprint.Flag::id).toList(), "flag", b.flags);
            allocate(source.state().sets().stream().map(ParsedGameBlueprint.FiniteSet::id).toList(), "set", b.sets);
            for (int i = 0; i < source.scenes().size(); i++) {
                ParsedGameBlueprint.Scene scene = source.scenes().get(i);
                LinkedHashMap<String, String> local = new LinkedHashMap<>();
                if (scene instanceof ParsedGameBlueprint.DecisionScene decision)
                    for (int j = 0; j < decision.choices().size(); j++) local.put(decision.choices().get(j).id(), "choice-" + pad(i + 1) + "-" + pad(j + 1));
                b.choices.put(sceneId(scene), local);
            }
            for (int i = 0; i < source.state().sets().size(); i++) {
                ParsedGameBlueprint.FiniteSet set = source.state().sets().get(i);
                LinkedHashMap<String, String> members = new LinkedHashMap<>();
                for (int j = 0; j < set.values().size(); j++) members.put(set.values().get(j), "set-" + pad(i + 1) + "-value-" + pad(j + 1));
                b.setMembers.put(set.id(), members);
            }
            return b;
        }
        private static void allocate(List<String> sources, String prefix, Map<String, String> target) {
            for (int i = 0; i < sources.size(); i++) target.put(sources.get(i), prefix + "-" + pad(i + 1));
        }
        MaterializedGameDefinition.IdBindings publicBindings() {
            return new MaterializedGameDefinition.IdBindings(actors, objectives, scenes, choices, outcomes, counters, flags, sets, setMembers);
        }
    }
}
