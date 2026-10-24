package com.capybee.engine.generation.profile;

import com.capybee.engine.content.Scene;
import com.capybee.engine.content.json.SceneSets;
import com.capybee.engine.generation.GeneratedGameProfile;
import com.capybee.engine.pack.Pack;
import com.capybee.engine.rules.Condition;
import com.capybee.engine.rules.Effect;
import com.capybee.engine.rules.json.RulesParser;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Verifies reviewed fallback claims against already validated local packs. */
public final class FallbackBindingValidator {
    public List<ProfileIssue> validate(ValidatedGeneratorProfile validated, Collection<Pack> packs) {
        var issues = new ArrayList<ProfileIssue>();
        for (var ref : validated.profile().fallbacks()) {
            Pack pack = packs.stream().filter(p -> p.manifest().id().equals(ref.packId())
                    && p.manifest().version().equals(ref.packVersion())).findFirst().orElse(null);
            if (pack == null) { issue(issues); continue; }
            var generated = pack.generatedGameProfile().orElse(null);
            var length = validated.profile().lengthPresets().stream().filter(p -> p.id().equals(ref.lengthPresetId())).findFirst().orElseThrow();
            var challenge = validated.profile().challengePresets().stream().filter(p -> p.id().equals(ref.challengePresetId())).findFirst().orElseThrow();
            if (!pack.manifest().locales().contains(ref.locale()) || generated == null
                    || generated.kind() != GeneratedGameProfile.Kind.FULL_GAME
                    || generated.minimumStoryTransitions() < length.minStoryTransitions()
                    || generated.maximumStoryTransitions() > length.maxStoryTransitions()) {
                issue(issues); continue;
            }
            var rules = RulesParser.parse(pack);
            if (rules.counters().size() > challenge.maxCounters() || rules.flags().size() > challenge.maxFlags()
                    || rules.sets().size() > challenge.maxSets()) { issue(issues); continue; }
            Set<String> conditions = new HashSet<>(), effects = new HashSet<>();
            rules.objectives().forEach(o -> { condition(o.completeWhen(), conditions); o.onComplete().forEach(e -> effect(e, effects)); });
            for (Scene scene : SceneSets.load(pack).get(ref.locale()).scenes().values()) {
                scene.onEnter().forEach(e -> effect(e, effects));
                scene.branches().forEach(b -> condition(b.condition(), conditions));
                scene.options().forEach(o -> { condition(o.requires(), conditions); o.effects().forEach(e -> effect(e, effects)); });
            }
            if (!challenge.allowedConditions().containsAll(conditions) || !challenge.allowedEffects().containsAll(effects)) issue(issues);
        }
        return List.copyOf(issues);
    }

    private static void condition(Condition value, Set<String> found) {
        if (value == null) return;
        String name = switch (value) {
            case Condition.CounterAtLeast ignored -> "COUNTER_AT_LEAST"; case Condition.CounterAtMost ignored -> "COUNTER_AT_MOST";
            case Condition.CounterEquals ignored -> "COUNTER_EQUALS"; case Condition.FlagIs ignored -> "FLAG_IS";
            case Condition.SetContains ignored -> "SET_CONTAINS"; case Condition.SetSizeAtLeast ignored -> "SET_SIZE_AT_LEAST";
            case Condition.VisitedScene ignored -> "VISITED_SCENE"; case Condition.All ignored -> "ALL";
            case Condition.Any ignored -> "ANY"; case Condition.Not ignored -> "NOT";
            default -> "UNSUPPORTED";
        }; found.add(name);
        if (value instanceof Condition.All all) all.conditions().forEach(v -> condition(v, found));
        else if (value instanceof Condition.Any any) any.conditions().forEach(v -> condition(v, found));
        else if (value instanceof Condition.Not not) condition(not.condition(), found);
    }
    private static void effect(Effect value, Set<String> found) {
        found.add(switch (value) {
            case Effect.CounterAdd ignored -> "COUNTER_ADD"; case Effect.CounterSet ignored -> "COUNTER_SET";
            case Effect.FlagSet ignored -> "FLAG_SET"; case Effect.SetAdd ignored -> "SET_ADD";
            case Effect.SetRemove ignored -> "SET_REMOVE"; default -> "UNSUPPORTED";
        });
    }
    private static void issue(List<ProfileIssue> issues) {
        issues.add(new ProfileIssue("PROFILE_FALLBACK_INVALID", "fallback", "", "Fallback does not honor the declared settings"));
    }
}
