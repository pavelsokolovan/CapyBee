package com.capybee.engine.generation.profile;

import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.PackId;
import com.capybee.engine.pack.PackVersion;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record GeneratorProfile(int schemaVersion, PackId id, PackVersion version,
        String titleKey, String descriptionKey, List<LocaleTag> supportedLocales,
        LocaleTag defaultLocale, ProfilePolicy policy, ProfileCapabilities capabilities,
        List<LengthPreset> lengthPresets, List<ChallengePreset> challengePresets,
        ProfileGenerationBudget generationBudget, ProfileSetup setup,
        Map<LocaleTag, Map<String, String>> strings, Map<LocaleTag, String> blueprintPrompts,
        Map<LocaleTag, String> qualityResources, List<FallbackRef> fallbacks, List<String> files) {
    public GeneratorProfile {
        supportedLocales = List.copyOf(supportedLocales); lengthPresets = List.copyOf(lengthPresets);
        challengePresets = List.copyOf(challengePresets); strings = Map.copyOf(strings);
        blueprintPrompts = Map.copyOf(blueprintPrompts); qualityResources = Map.copyOf(qualityResources);
        fallbacks = List.copyOf(fallbacks); files = List.copyOf(files);
    }
    public record ProfilePolicy(int minAge, int maxAge, boolean freeTextAllowed,
                                int maxSuggestionCodePoints, Set<String> forbiddenTags) {
        public ProfilePolicy { forbiddenTags = Set.copyOf(forbiddenTags); }
    }
    public record FallbackRef(PackId packId, PackVersion packVersion, LocaleTag locale,
                              String lengthPresetId, String challengePresetId) {}
}
