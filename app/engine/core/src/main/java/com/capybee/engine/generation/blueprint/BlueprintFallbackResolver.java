package com.capybee.engine.generation.blueprint;

import com.capybee.engine.generation.SceneWordLimits;
import com.capybee.engine.generation.profile.ResolvedGamePreferences;
import com.capybee.engine.generation.blueprint.BlueprintGenerationResult.FallbackOffer;
import java.util.Optional;

/** Host capability backed by already validated, reviewed fallback bindings. */
@FunctionalInterface
public interface BlueprintFallbackResolver {
    BlueprintFallbackResolver NONE = preferences -> Optional.empty();
    Optional<FallbackOffer> matchingReviewedFallback(ResolvedGamePreferences preferences);

    /** Hosts may offer an opted-in binding after verifying its reviewed copy against these active limits. */
    default Optional<FallbackOffer> matchingReviewedFallback(ResolvedGamePreferences preferences, SceneWordLimits limits) {
        return limits == null ? matchingReviewedFallback(preferences) : Optional.empty();
    }
}
