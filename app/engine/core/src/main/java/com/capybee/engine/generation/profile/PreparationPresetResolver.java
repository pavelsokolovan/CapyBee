package com.capybee.engine.generation.profile;

import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.StartInputDef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class PreparationPresetResolver {
    private static final Set<String> CREATIVE = Set.of("genre", "setting", "tone", "interests");

    public PreparationResolution resolve(ValidatedGeneratorProfile validated,
            Map<String, PreparationAnswer> submitted, LocaleTag requestedLocale,
            HostGenerationCeilings ceilings) {
        var profile = validated.profile();
        var issues = new ArrayList<ProfileIssue>();
        var inputs = new LinkedHashMap<String, StartInputDef>();
        profile.setup().inputs().forEach(input -> inputs.put(input.id(), input));
        for (String id : submitted.keySet()) {
            if (id.equals("story-wish")) issue(issues, "PREPARATION_TRANSIENT_INPUT_REQUIRED");
            else if (!inputs.containsKey(id)) issue(issues, "PREPARATION_INPUT_INVALID");
        }
        Map<String, List<String>> values = new LinkedHashMap<>();
        for (int i = 0; i < profile.setup().inputs().size(); i++) {
            StartInputDef input = profile.setup().inputs().get(i);
            if (input instanceof StartInputDef.FreeTextInput) continue;
            PreparationAnswer answer = submitted.get(input.id());
            List<String> chosen;
            if (answer == null) chosen = profile.setup().questions().get(i).defaultValues();
            else if (answer instanceof PreparationAnswer.Cleared) chosen = List.of();
            else chosen = ((PreparationAnswer.Selected) answer).values();
            validate(input, chosen, issues);
            values.put(input.id(), canonical(input, chosen));
        }
        String wizardLocale = first(values.get("game-locale"));
        PreparationAnswer localeAnswer = submitted.get("game-locale");
        String explicitWizardLocale = localeAnswer instanceof PreparationAnswer.Selected selected
                ? first(selected.values()) : null;
        if (requestedLocale != null && explicitWizardLocale != null
                && !requestedLocale.value().equals(explicitWizardLocale)) {
            issue(issues, "PREPARATION_LOCALE_CONFLICT");
        }
        String localeText = requestedLocale != null ? requestedLocale.value()
                : explicitWizardLocale != null ? explicitWizardLocale
                : wizardLocale != null ? wizardLocale : profile.defaultLocale().value();
        if (profile.supportedLocales().stream().noneMatch(it -> it.value().equals(localeText))) issue(issues, "PREPARATION_INPUT_INVALID");
        String lengthId = first(values.get("length"));
        String challengeId = first(values.get("challenge"));
        LengthPreset length = profile.lengthPresets().stream().filter(it -> it.id().equals(lengthId)).findFirst().orElse(null);
        ChallengePreset challenge = profile.challengePresets().stream().filter(it -> it.id().equals(challengeId)).findFirst().orElse(null);
        if (length == null || challenge == null) issue(issues, "PREPARATION_INPUT_INVALID");
        if (!issues.isEmpty()) return new PreparationResolution(Optional.empty(), Optional.empty(), issues);

        ceilings = ceilings == null ? HostGenerationCeilings.none() : ceilings;
        int maxScenes = narrow(length.maxScenes(), ceilings.maxScenes());
        int counters = narrow(challenge.maxCounters(), ceilings.maxCounters());
        int flags = narrow(challenge.maxFlags(), ceilings.maxFlags());
        int sets = narrow(challenge.maxSets(), ceilings.maxSets());
        int depth = narrow(challenge.maxConditionDepth(), ceilings.maxConditionDepth());
        int effects = narrow(challenge.maxEffectsPerChoice(), ceilings.maxEffectsPerChoice());
        int calls = narrow(profile.generationBudget().totalCallsPerRun(), ceilings.totalCallsPerRun());
        if (maxScenes < profile.capabilities().minScenes() || calls < 1 + length.maxStoryTransitions() + 1
                || depth < 1 || (counters == 0 && needs(challenge, "COUNTER_"))
                || (flags == 0 && needs(challenge, "FLAG_")) || (sets == 0 && needs(challenge, "SET_"))) {
            issue(issues, "PREPARATION_CONSTRAINTS_UNSATISFIABLE");
            return new PreparationResolution(Optional.empty(), Optional.empty(), issues);
        }
        var prefs = new ResolvedGamePreferences(validated.identity(), new LocaleTag(localeText), length.id(), challenge.id(),
                optional(values, "genre"), optional(values, "setting"), optional(values, "tone"),
                values.getOrDefault("interests", List.of()));
        var constraints = new EffectiveGenerationConstraints(length.minStoryTransitions(), length.maxStoryTransitions(),
                profile.capabilities().minScenes(), maxScenes, counters, flags, sets, depth, effects,
                challenge.allowedConditions(), challenge.allowedEffects(), calls,
                narrow(profile.generationBudget().blueprintMaxOutputTokens(), ceilings.blueprintMaxOutputTokens()),
                narrow(profile.generationBudget().sceneMaxOutputTokens(), ceilings.sceneMaxOutputTokens()),
                narrow(profile.generationBudget().maxInputCharacters(), ceilings.maxInputCharacters()));
        return new PreparationResolution(Optional.of(prefs), Optional.of(constraints), List.of());
    }

    private static void validate(StartInputDef def, List<String> chosen, List<ProfileIssue> issues) {
        if (def.required() && chosen.isEmpty()) issue(issues, "PREPARATION_INPUT_INVALID");
        if (def instanceof StartInputDef.EnumInput item) {
            if (chosen.size() > 1 || !item.values().containsAll(chosen)) issue(issues, "PREPARATION_INPUT_INVALID");
        } else if (def instanceof StartInputDef.EnumSetInput item) {
            if (chosen.size() > item.maxItems() || !item.values().containsAll(chosen)
                    || new LinkedHashSet<>(chosen).size() != chosen.size()) issue(issues, "PREPARATION_INPUT_INVALID");
        }
    }
    private static List<String> canonical(StartInputDef def, List<String> values) {
        if (!(def instanceof StartInputDef.EnumSetInput set)) return List.copyOf(values);
        return set.values().stream().filter(values::contains).toList();
    }
    private static boolean needs(ChallengePreset preset, String prefix) {
        return preset.allowedConditions().stream().anyMatch(it -> it.startsWith(prefix))
                || preset.allowedEffects().stream().anyMatch(it -> it.startsWith(prefix));
    }
    private static int narrow(int value, Integer ceiling) { return ceiling == null ? value : Math.min(value, ceiling); }
    private static String first(List<String> values) { return values == null || values.isEmpty() ? null : values.getFirst(); }
    private static Optional<String> optional(Map<String, List<String>> values, String key) { return Optional.ofNullable(first(values.get(key))); }
    private static void issue(List<ProfileIssue> issues, String code) {
        if (issues.size() < 100) issues.add(new ProfileIssue(code, "preparation", "", code));
    }
}
