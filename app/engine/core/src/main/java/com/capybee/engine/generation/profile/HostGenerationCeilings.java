package com.capybee.engine.generation.profile;

/** Optional trusted host ceilings; null fields mean no additional narrowing. */
public record HostGenerationCeilings(Integer maxScenes, Integer maxCounters, Integer maxFlags,
        Integer maxSets, Integer maxConditionDepth, Integer maxEffectsPerChoice,
        Integer totalCallsPerRun, Integer blueprintMaxOutputTokens,
        Integer sceneMaxOutputTokens, Integer maxInputCharacters) {
    public static HostGenerationCeilings none() {
        return new HostGenerationCeilings(null, null, null, null, null, null, null, null, null, null);
    }
}
