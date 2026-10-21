package com.capybee.engine.generation;
import java.util.List;
public record GeneratedGameProfile(Kind kind, int contractVersion, List<PhaseProfile> phases,
        int reachableStoryScenes, int endings, int minimumStoryTransitions,
        int maximumStoryTransitions, int minimumRequiredProviderCalls) {
    public GeneratedGameProfile { phases = List.copyOf(phases); }
    public enum Kind { CONTENT_ONLY, FULL_GAME }
    public static GeneratedGameProfile contentOnly() {
        return new GeneratedGameProfile(Kind.CONTENT_ONLY, 0, List.of(), 0, 0, 0, 0, 0);
    }
}
