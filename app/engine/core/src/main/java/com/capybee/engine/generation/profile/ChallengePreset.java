package com.capybee.engine.generation.profile;

import java.util.Set;

public record ChallengePreset(String id, String labelKey, int maxCounters, int maxFlags,
                              int maxSets, int maxConditionDepth, int maxEffectsPerChoice,
                              Set<String> allowedConditions, Set<String> allowedEffects) {
    public ChallengePreset {
        allowedConditions = Set.copyOf(allowedConditions);
        allowedEffects = Set.copyOf(allowedEffects);
    }
}
