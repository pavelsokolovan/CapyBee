package com.capybee.engine.generation.profile;

import com.capybee.engine.generation.PhaseKind;
import java.util.List;
import java.util.Set;

public record ProfileCapabilities(
        List<PhaseKind> phaseKinds, String graphKind, boolean placesEnabled,
        int minScenes, int maxScenes, int maxChoicesPerScene,
        int minActors, int maxActors, int minObjectives, int maxObjectives,
        int minEndings, int maxEndings, int maxCounters, int maxFlags, int maxSets,
        int maxSetValues, int counterMin, int counterMax, int maxCounterDelta,
        int maxConditionDepth, int maxConditionNodes, int maxEffectsPerChoice,
        int maxExploredStates, Set<String> allowedConditions, Set<String> allowedEffects) {
    public ProfileCapabilities {
        phaseKinds = List.copyOf(phaseKinds);
        allowedConditions = Set.copyOf(allowedConditions);
        allowedEffects = Set.copyOf(allowedEffects);
    }
}
