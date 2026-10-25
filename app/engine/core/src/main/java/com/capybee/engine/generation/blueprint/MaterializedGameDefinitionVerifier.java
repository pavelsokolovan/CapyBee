package com.capybee.engine.generation.blueprint;

import com.capybee.engine.content.Scene;
import com.capybee.engine.content.SceneOption;
import com.capybee.engine.rules.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Independent structural verifier for compiled dynamic definitions. */
public final class MaterializedGameDefinitionVerifier {
    private static final Pattern TRUSTED_ID = Pattern.compile("(?:actor|objective|scene|outcome|counter|flag|set)-\\d{3}(?:-value-\\d{3})?|choice-\\d{3}-\\d{3}|phase-(?:opening|development|challenge|resolution|ending)");

    public Verification verify(MaterializedGameDefinition definition) {
        if (definition == null || definition.definitionFormatVersion() != MaterializedGameDefinition.FORMAT_VERSION
                || !MaterializedGameCompiler.COMPILER_VERSION.equals(definition.compilerVersion()))
            return fail("MATERIALIZED_DEFINITION_VERSION_UNSUPPORTED");
        if (definition.origin() != MaterializedGameDefinition.Origin.DYNAMIC_BLUEPRINT
                || !GeneratedMechanicsV1.CONTRACT_VERSION.equals(definition.generatedRulesContractVersion())
                || definition.profileIdentity() == null || definition.locale() == null
                || blank(definition.semanticValidatorVersion()) || blank(definition.outputReviewerVersion()))
            return fail("MATERIALIZED_DEFINITION_PROVENANCE_INVALID");
        if (!bindingsValid(definition)) return fail("MATERIALIZED_DEFINITION_BINDING_INVALID");
        if (!scenesValid(definition)) return fail("MATERIALIZED_DEFINITION_SCENE_INVALID");
        if (!rulesValid(definition)) return fail("MATERIALIZED_DEFINITION_RULE_INVALID");
        if (!planningValid(definition) || !contentValid(definition)) return fail("MATERIALIZED_DEFINITION_CONTENT_INVALID");
        if (!evidenceValid(definition)) return fail("MATERIALIZED_DEFINITION_EVIDENCE_MISMATCH");
        return new Verification(true, "MATERIALIZED_DEFINITION_VALID");
    }

    private boolean bindingsValid(MaterializedGameDefinition d) {
        MaterializedGameDefinition.IdBindings b = d.idBindings();
        if (b == null) return false;
        if (!uniqueTrusted(b.actors()) || !uniqueTrusted(b.objectives()) || !uniqueTrusted(b.scenes())
                || !uniqueTrusted(b.outcomes()) || !uniqueTrusted(b.counters()) || !uniqueTrusted(b.flags())
                || !uniqueTrusted(b.sets())) return false;
        Set<String> all = new HashSet<>();
        for (Map<String, String> choices : b.choices().values()) if (!uniqueTrusted(choices) || !addUnique(all, choices.values())) return false;
        for (Map<String, String> members : b.setMembers().values()) if (!uniqueTrusted(members) || !addUnique(all, members.values())) return false;
        return b.choices().keySet().equals(b.scenes().keySet()) && b.setMembers().keySet().equals(b.sets().keySet())
                && values(b.actors()).equals(ids(d.actors().stream().map(MaterializedGameDefinition.ActorPlan::id).toList()))
                && values(b.scenes()).equals(ids(d.canonicalScenes().stream().map(Scene::id).toList()))
                && values(b.objectives()).equals(ids(d.runtimeRules().objectives().stream().map(ObjectiveDef::id).toList()))
                && values(b.outcomes()).equals(ids(d.runtimeRules().outcomes().stream().map(OutcomeDef::id).toList()))
                && values(b.counters()).equals(ids(d.runtimeRules().counters().stream().map(CounterDef::id).toList()))
                && values(b.flags()).equals(ids(d.runtimeRules().flags().stream().map(FlagDef::id).toList()))
                && values(b.sets()).equals(ids(d.runtimeRules().sets().stream().map(SetDef::id).toList()));
    }

    private boolean scenesValid(MaterializedGameDefinition d) {
        if (!d.runtimeSceneSet().locale().equals(d.locale()) || d.canonicalScenes().size() != d.runtimeSceneSet().scenes().size()) return false;
        Set<String> sceneIds = ids(d.canonicalScenes().stream().map(Scene::id).toList());
        if (!sceneIds.equals(d.runtimeSceneSet().scenes().keySet()) || !sceneIds.contains(d.runtimeSceneSet().startId())) return false;
        int starts = 0;
        Set<String> endingOutcomes = new HashSet<>();
        for (Scene scene : d.canonicalScenes()) {
            if (scene.start()) starts++;
            if (scene.start() != scene.id().equals(d.runtimeSceneSet().startId()) || !scene.blocks().isEmpty()
                    || scene.next() != null || !scene.onEnter().isEmpty() || !scene.branches().isEmpty()
                    || scene.inputMode() != null || scene.maxChars() != 0) return false;
            if (scene.end()) {
                if (!scene.options().isEmpty() || scene.outcome() == null || !endingOutcomes.add(scene.outcome())) return false;
            } else {
                if (scene.outcome() != null || scene.options().isEmpty()) return false;
                for (SceneOption option : scene.options()) {
                    if (!MaterializedGameCompiler.UNRESOLVED_LABEL.equals(option.label()) || !sceneIds.contains(option.next())
                            || option.unmetHint() != null || option.hideWhenUnmet()
                            || !conditionValid(option.requires(), d) || !effectsValid(option.effects(), d)) return false;
                }
            }
        }
        return starts == 1 && endingOutcomes.equals(ids(d.runtimeRules().outcomes().stream().map(OutcomeDef::id).toList()));
    }

    private boolean rulesValid(MaterializedGameDefinition d) {
        Rules r = d.runtimeRules();
        Set<String> counters = ids(r.counters().stream().map(CounterDef::id).toList());
        Set<String> flags = ids(r.flags().stream().map(FlagDef::id).toList());
        Set<String> sets = ids(r.sets().stream().map(SetDef::id).toList());
        if (!r.resultFacts().isEmpty()) return false;
        for (CounterDef value : r.counters()) if (value.min() > value.initial() || value.initial() > value.max() || value.display()
                || value.maxDelta() != null && value.maxDelta() < 0) return false;
        for (SetDef value : r.sets()) if (!new HashSet<>(value.values()).containsAll(value.initial())
                || value.values().size() != new HashSet<>(value.values()).size()) return false;
        for (ObjectiveDef value : r.objectives()) if (!value.onComplete().isEmpty() || value.labelKey() == null
                || !conditionValid(value.completeWhen(), d)) return false;
        for (OutcomeDef value : r.outcomes()) if (value.resultClass() == null || value.labelKey() == null) return false;
        return counters.equals(values(d.idBindings().counters())) && flags.equals(values(d.idBindings().flags()))
                && sets.equals(values(d.idBindings().sets()));
    }

    private boolean conditionValid(Condition c, MaterializedGameDefinition d) {
        if (c == null) return true;
        Set<String> counters = values(d.idBindings().counters()), flags = values(d.idBindings().flags());
        Set<String> sets = values(d.idBindings().sets()), scenes = values(d.idBindings().scenes());
        if (c instanceof Condition.CounterAtLeast v) return counters.contains(v.counter());
        if (c instanceof Condition.CounterAtMost v) return counters.contains(v.counter());
        if (c instanceof Condition.CounterEquals v) return counters.contains(v.counter());
        if (c instanceof Condition.FlagIs v) return flags.contains(v.flag());
        if (c instanceof Condition.SetContains v) return sets.contains(v.set()) && setValues(d, v.set()).contains(v.value());
        if (c instanceof Condition.SetSizeAtLeast v) return sets.contains(v.set());
        if (c instanceof Condition.VisitedScene v) return scenes.contains(v.scene());
        if (c instanceof Condition.All v) return v.conditions().stream().allMatch(value -> conditionValid(value, d));
        if (c instanceof Condition.Any v) return v.conditions().stream().allMatch(value -> conditionValid(value, d));
        if (c instanceof Condition.Not v) return conditionValid(v.condition(), d);
        return false;
    }

    private boolean effectsValid(List<Effect> effects, MaterializedGameDefinition d) {
        for (Effect effect : effects) {
            if (effect instanceof Effect.CounterAdd v && values(d.idBindings().counters()).contains(v.counter())) continue;
            if (effect instanceof Effect.CounterSet v && values(d.idBindings().counters()).contains(v.counter())) continue;
            if (effect instanceof Effect.FlagSet v && values(d.idBindings().flags()).contains(v.flag())) continue;
            if (effect instanceof Effect.SetAdd v && values(d.idBindings().sets()).contains(v.set()) && setValues(d, v.set()).contains(v.value())) continue;
            if (effect instanceof Effect.SetRemove v && values(d.idBindings().sets()).contains(v.set()) && setValues(d, v.set()).contains(v.value())) continue;
            return false;
        }
        return true;
    }

    private Set<String> setValues(MaterializedGameDefinition d, String trustedSet) {
        String source = d.idBindings().sets().entrySet().stream().filter(e -> e.getValue().equals(trustedSet))
                .map(Map.Entry::getKey).findFirst().orElse(null);
        return source == null ? Set.of() : values(d.idBindings().setMembers().get(source));
    }

    private boolean planningValid(MaterializedGameDefinition d) {
        Set<String> actors = values(d.idBindings().actors()), objectives = values(d.idBindings().objectives());
        Set<String> scenes = values(d.idBindings().scenes());
        if (d.scenePlans().size() != d.canonicalScenes().size()) return false;
        Set<String> phaseScenes = new HashSet<>();
        for (MaterializedGameDefinition.PhasePlan phase : d.phases()) {
            if (!TRUSTED_ID.matcher(phase.id()).matches() || !actors.containsAll(phase.actorIds())
                    || !objectives.containsAll(phase.objectiveIds()) || !scenes.containsAll(phase.sceneIds())
                    || !addUnique(phaseScenes, phase.sceneIds())) return false;
        }
        if (!phaseScenes.equals(scenes)) return false;
        for (MaterializedGameDefinition.ScenePlan scene : d.scenePlans()) {
            if (!scenes.contains(scene.id()) || !actors.containsAll(scene.actorIds()) || !objectives.containsAll(scene.objectiveIds())) return false;
            Set<String> runtimeChoices = new HashSet<>();
            d.runtimeSceneSet().scenes().get(scene.id()).options().forEach(option -> runtimeChoices.add(option.id()));
            if (!runtimeChoices.equals(ids(scene.choices().stream().map(MaterializedGameDefinition.ChoicePlan::id).toList()))) return false;
        }
        return true;
    }

    private boolean contentValid(MaterializedGameDefinition d) {
        Map<String, String> c = d.localizedContent();
        if (c.size() != new HashSet<>(c.keySet()).size() || !c.containsKey("story.title") || c.values().stream().anyMatch(value -> value == null)) return false;
        for (int i = 0; i < d.actors().size(); i++) if (!c.containsKey(key("actor", i, "name"))) return false;
        for (int i = 0; i < d.runtimeRules().objectives().size(); i++)
            if (!c.containsKey(key("objective", i, "title")) || !c.containsKey(key("objective", i, "description"))) return false;
        for (int i = 0; i < d.runtimeRules().outcomes().size(); i++)
            if (!c.containsKey(key("outcome", i, "title")) || !c.containsKey(key("outcome", i, "description"))) return false;
        for (int i = 0; i < d.scenePlans().size(); i++) {
            if (!c.containsKey(key("scene", i, "title"))) return false;
            for (int j = 0; j < d.scenePlans().get(i).choices().size(); j++)
                if (!c.containsKey("scene." + pad(i + 1) + ".choice." + pad(j + 1) + ".intent")) return false;
        }
        return true;
    }

    private boolean evidenceValid(MaterializedGameDefinition d) {
        MaterializedGameDefinition.CompilationEvidence e = d.compilationEvidence();
        int choices = d.canonicalScenes().stream().mapToInt(scene -> scene.options().size()).sum();
        return e.actorCount() == d.actors().size() && e.objectiveCount() == d.runtimeRules().objectives().size()
                && e.sceneCount() == d.canonicalScenes().size() && e.choiceCount() == choices && e.edgeCount() == choices
                && e.outcomeCount() == d.runtimeRules().outcomes().size() && e.counterCount() == d.runtimeRules().counters().size()
                && e.flagCount() == d.runtimeRules().flags().size() && e.setCount() == d.runtimeRules().sets().size()
                && e.exploredConfigurationCount() > 0 && e.minimumTransitions() >= 0
                && e.maximumTransitions() >= e.minimumTransitions();
    }

    private static boolean uniqueTrusted(Map<String, String> map) {
        return map != null && map.size() == new HashSet<>(map.values()).size()
                && map.values().stream().allMatch(value -> value != null && TRUSTED_ID.matcher(value).matches());
    }
    private static boolean addUnique(Set<String> destination, Iterable<String> values) {
        for (String value : values) if (!destination.add(value)) return false;
        return true;
    }
    private static Set<String> values(Map<String, String> map) { return map == null ? Set.of() : new HashSet<>(map.values()); }
    private static Set<String> ids(List<String> values) { return new HashSet<>(values); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static Verification fail(String code) { return new Verification(false, code); }
    private static String key(String kind, int index, String field) { return kind + "." + pad(index + 1) + "." + field; }
    private static String pad(int value) { return "%03d".formatted(value); }

    public record Verification(boolean valid, String code) {
        @Override public String toString() { return "Verification[valid=" + valid + ", code=" + code + "]"; }
    }
}
