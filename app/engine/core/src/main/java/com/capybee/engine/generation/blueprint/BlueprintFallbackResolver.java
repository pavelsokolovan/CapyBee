package com.capybee.engine.generation.blueprint;

import com.capybee.engine.generation.profile.ResolvedGamePreferences;
import com.capybee.engine.generation.blueprint.BlueprintGenerationResult.FallbackOffer;
import java.util.Optional;

/** Host capability backed by already validated, reviewed fallback bindings. */
@FunctionalInterface
public interface BlueprintFallbackResolver {
    BlueprintFallbackResolver NONE = preferences -> Optional.empty();
    Optional<FallbackOffer> matchingReviewedFallback(ResolvedGamePreferences preferences);
}
