package com.capybee.engine.generation.profile;

import com.capybee.engine.generation.PhaseKind;
import com.capybee.engine.rules.GeneratedMechanicsV1;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

/** Independently re-establishes the profile/context invariants at live generation boundaries. */
public final class GenerationProfileTrust {
    private static final Set<String> CONDITIONS = GeneratedMechanicsV1.conditionNames();
    private static final Set<String> EFFECTS = GeneratedMechanicsV1.effectNames();

    private GenerationProfileTrust() {}

    public static boolean validContext(ValidatedGeneratorProfile validated, ResolvedGamePreferences preferences,
                                       EffectiveGenerationConstraints limits) {
        if (validated == null || preferences == null || limits == null) return false;
        GeneratorProfile profile = validated.profile();
        if (profile == null || validated.identity() == null || !validated.identity().equals(preferences.profileIdentity())
                || !validated.identity().id().equals(profile.id()) || !validated.identity().version().equals(profile.version())
                || validated.identity().fingerprint() == null || validated.identity().fingerprint().isBlank()) return false;
        ProfileCapabilities cap = profile.capabilities();
        ProfileGenerationBudget budget = profile.generationBudget();
        if (cap == null || budget == null || profile.policy() == null
                || !cap.phaseKinds().equals(Arrays.asList(PhaseKind.values())) || !"FORWARD_DAG".equals(cap.graphKind())
                || cap.placesEnabled() || !CONDITIONS.containsAll(cap.allowedConditions())
                || !EFFECTS.containsAll(cap.allowedEffects()) || !validCapabilities(cap)
                || !validBudget(budget) || !validPolicy(profile.policy())) return false;
        LengthPreset length = profile.lengthPresets().stream()
                .filter(value -> Objects.equals(value.id(), preferences.lengthPresetId())).findFirst().orElse(null);
        ChallengePreset challenge = profile.challengePresets().stream()
                .filter(value -> Objects.equals(value.id(), preferences.challengePresetId())).findFirst().orElse(null);
        if (length == null || challenge == null || !profile.supportedLocales().contains(preferences.gameLocale())) return false;
        return limits.minStoryTransitions() == length.minStoryTransitions()
                && limits.maxStoryTransitions() == length.maxStoryTransitions()
                && limits.minScenes() == cap.minScenes()
                && limits.maxScenes() >= limits.minScenes() && limits.maxScenes() <= length.maxScenes()
                && between(limits.maxCounters(), 0, challenge.maxCounters())
                && between(limits.maxFlags(), 0, challenge.maxFlags())
                && between(limits.maxSets(), 0, challenge.maxSets())
                && between(limits.maxConditionDepth(), 1, challenge.maxConditionDepth())
                && between(limits.maxEffectsPerChoice(), 0, challenge.maxEffectsPerChoice())
                && challenge.allowedConditions().containsAll(limits.allowedConditions())
                && challenge.allowedEffects().containsAll(limits.allowedEffects())
                && cap.allowedConditions().containsAll(limits.allowedConditions())
                && cap.allowedEffects().containsAll(limits.allowedEffects())
                && between(limits.totalCallsPerRun(), 1, budget.totalCallsPerRun())
                && between(limits.blueprintMaxOutputTokens(), 512, budget.blueprintMaxOutputTokens())
                && between(limits.sceneMaxOutputTokens(), 128, budget.sceneMaxOutputTokens())
                && between(limits.maxInputCharacters(), 1024, budget.maxInputCharacters());
    }

    public static boolean validForGeneration(ValidatedGeneratorProfile validated,
                                             ResolvedGamePreferences preferences,
                                             EffectiveGenerationConstraints limits) {
        if (!validContext(validated, preferences, limits)) return false;
        GeneratorProfile profile = validated.profile();
        String prompt = profile.blueprintPrompts().get(preferences.gameLocale());
        String quality = profile.qualityResources().get(preferences.gameLocale());
        return prompt != null && !prompt.isBlank() && quality != null && !quality.isBlank()
                && profile.generationBudget().blueprintMaxAttempts() >= 1
                && profile.generationBudget().blueprintMaxAttempts() <= 2;
    }

    private static boolean between(int value, int minimum, int maximum) {
        return value >= minimum && value <= maximum;
    }

    private static boolean validCapabilities(ProfileCapabilities c) {
        return between(c.minScenes(),6,c.maxScenes()) && c.maxScenes()<=24
                && between(c.maxChoicesPerScene(),2,4) && between(c.minActors(),1,c.maxActors()) && c.maxActors()<=12
                && between(c.minObjectives(),1,c.maxObjectives()) && c.maxObjectives()<=8
                && between(c.minEndings(),2,c.maxEndings()) && c.maxEndings()<=4
                && between(c.maxCounters(),0,4) && between(c.maxFlags(),0,12) && between(c.maxSets(),0,2)
                && between(c.maxSetValues(),1,8) && c.counterMin()>=0 && c.counterMin()<c.counterMax() && c.counterMax()<=20
                && between(c.maxCounterDelta(),1,5) && c.maxCounterDelta()<=c.counterMax()-c.counterMin()
                && between(c.maxConditionDepth(),1,4) && between(c.maxConditionNodes(),1,32)
                && between(c.maxEffectsPerChoice(),0,6) && between(c.maxExploredStates(),1,100_000);
    }
    private static boolean validBudget(ProfileGenerationBudget b) {
        return between(b.totalCallsPerRun(),1,64) && between(b.blueprintMaxAttempts(),1,2)
                && between(b.blueprintMaxOutputTokens(),512,12_000) && between(b.sceneMaxAttempts(),1,2)
                && between(b.sceneMaxOutputTokens(),128,1_500) && between(b.maxInputCharacters(),1024,32_000);
    }
    private static boolean validPolicy(GeneratorProfile.ProfilePolicy p) {
        return p.minAge()>=0 && p.maxAge()>=p.minAge() && p.maxAge()<=17
                && (p.freeTextAllowed() ? between(p.maxSuggestionCodePoints(),1,300) : p.maxSuggestionCodePoints()==0);
    }
}
