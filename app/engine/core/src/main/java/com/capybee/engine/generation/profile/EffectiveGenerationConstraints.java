package com.capybee.engine.generation.profile;

import java.util.Set;

public record EffectiveGenerationConstraints(int minStoryTransitions, int maxStoryTransitions,
        int minScenes, int maxScenes, int maxCounters, int maxFlags, int maxSets,
        int maxConditionDepth, int maxEffectsPerChoice, Set<String> allowedConditions,
        Set<String> allowedEffects, int totalCallsPerRun, int blueprintMaxOutputTokens,
        int sceneMaxOutputTokens, int maxInputCharacters) {
    public EffectiveGenerationConstraints {
        allowedConditions = Set.copyOf(allowedConditions); allowedEffects = Set.copyOf(allowedEffects);
    }
}
