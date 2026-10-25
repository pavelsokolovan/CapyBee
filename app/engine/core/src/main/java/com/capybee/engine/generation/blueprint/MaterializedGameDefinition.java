package com.capybee.engine.generation.blueprint;

import com.capybee.engine.content.Scene;
import com.capybee.engine.content.SceneSet;
import com.capybee.engine.generation.PhaseKind;
import com.capybee.engine.generation.profile.ProfileIdentity;
import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.rules.Rules;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable, definition-local result of compiling a reviewed dynamic blueprint. */
public record MaterializedGameDefinition(
        int definitionFormatVersion,
        String compilerVersion,
        Origin origin,
        ProfileIdentity profileIdentity,
        LocaleTag locale,
        String lengthPresetId,
        String challengePresetId,
        int blueprintSchemaVersion,
        String generatedRulesContractVersion,
        String semanticValidatorVersion,
        String outputReviewerVersion,
        StoryPlan storyPlan,
        List<ActorPlan> actors,
        List<PhasePlan> phases,
        List<ScenePlan> scenePlans,
        List<Scene> canonicalScenes,
        SceneSet runtimeSceneSet,
        Rules runtimeRules,
        Map<String, String> localizedContent,
        IdBindings idBindings,
        CompilationEvidence compilationEvidence) {

    public static final int FORMAT_VERSION = 1;

    public MaterializedGameDefinition {
        Objects.requireNonNull(compilerVersion);
        Objects.requireNonNull(origin);
        Objects.requireNonNull(profileIdentity);
        Objects.requireNonNull(locale);
        Objects.requireNonNull(lengthPresetId);
        Objects.requireNonNull(challengePresetId);
        Objects.requireNonNull(generatedRulesContractVersion);
        Objects.requireNonNull(semanticValidatorVersion);
        Objects.requireNonNull(outputReviewerVersion);
        Objects.requireNonNull(storyPlan);
        actors = List.copyOf(actors);
        phases = List.copyOf(phases);
        scenePlans = List.copyOf(scenePlans);
        canonicalScenes = List.copyOf(canonicalScenes);
        Objects.requireNonNull(runtimeSceneSet);
        Objects.requireNonNull(runtimeRules);
        localizedContent = orderedCopy(localizedContent);
        Objects.requireNonNull(idBindings);
        Objects.requireNonNull(compilationEvidence);
    }

    private static <K, V> Map<K, V> orderedCopy(Map<K, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    @Override public String toString() {
        return "MaterializedGameDefinition[version=" + definitionFormatVersion
                + ", compilerVersion=" + compilerVersion + ", origin=" + origin + ", redacted]";
    }

    public enum Origin { DYNAMIC_BLUEPRINT }

    public record StoryPlan(String title, String premise, String setting,
                            List<String> toneTags, List<String> storyPromises) {
        public StoryPlan { toneTags = List.copyOf(toneTags); storyPromises = List.copyOf(storyPromises); }
        @Override public String toString() { return "StoryPlan[redacted]"; }
    }

    public record ActorPlan(String id, String name, String role, String description) {
        @Override public String toString() { return "ActorPlan[id=" + id + ", content=redacted]"; }
    }

    public record PhasePlan(String id, PhaseKind kind, String goal, String development, String transition,
                            List<String> actorIds, List<String> objectiveIds, List<String> sceneIds) {
        public PhasePlan {
            actorIds = List.copyOf(actorIds); objectiveIds = List.copyOf(objectiveIds); sceneIds = List.copyOf(sceneIds);
        }
        @Override public String toString() { return "PhasePlan[id=" + id + ", kind=" + kind + ", content=redacted]"; }
    }

    public record ScenePlan(String id, PhaseKind phase, String title, String intent,
                            List<String> actorIds, List<String> objectiveIds, List<ChoicePlan> choices) {
        public ScenePlan {
            actorIds = List.copyOf(actorIds); objectiveIds = List.copyOf(objectiveIds); choices = List.copyOf(choices);
        }
        @Override public String toString() { return "ScenePlan[id=" + id + ", phase=" + phase + ", content=redacted]"; }
    }

    public record ChoicePlan(String id, String intent) {
        @Override public String toString() { return "ChoicePlan[id=" + id + ", content=redacted]"; }
    }

    /** Source symbols are retained only in this redacted provenance structure. */
    public record IdBindings(Map<String, String> actors, Map<String, String> objectives,
                             Map<String, String> scenes, Map<String, Map<String, String>> choices,
                             Map<String, String> outcomes, Map<String, String> counters,
                             Map<String, String> flags, Map<String, String> sets,
                             Map<String, Map<String, String>> setMembers) {
        public IdBindings {
            actors = orderedCopy(actors); objectives = orderedCopy(objectives); scenes = orderedCopy(scenes);
            choices = nestedOrderedCopy(choices); outcomes = orderedCopy(outcomes); counters = orderedCopy(counters);
            flags = orderedCopy(flags); sets = orderedCopy(sets); setMembers = nestedOrderedCopy(setMembers);
        }
        private static <K, A, B> Map<K, Map<A, B>> nestedOrderedCopy(Map<K, Map<A, B>> source) {
            LinkedHashMap<K, Map<A, B>> result = new LinkedHashMap<>();
            source.forEach((key, value) -> result.put(key, orderedCopy(value)));
            return Collections.unmodifiableMap(result);
        }
        @Override public String toString() { return "IdBindings[redacted]"; }
    }

    public record CompilationEvidence(int actorCount, int objectiveCount, int sceneCount, int choiceCount,
                                      int outcomeCount, int counterCount, int flagCount, int setCount,
                                      int edgeCount, int exploredConfigurationCount,
                                      int minimumTransitions, int maximumTransitions) {}
}
