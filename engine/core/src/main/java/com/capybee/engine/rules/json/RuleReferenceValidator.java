package com.capybee.engine.rules.json;

import com.capybee.engine.content.Scene;
import com.capybee.engine.content.SceneOption;
import com.capybee.engine.content.SceneSet;
import com.capybee.engine.pack.EventDef;
import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.Pack;
import com.capybee.engine.pack.PackValidationContext;
import com.capybee.engine.pack.SectionValidator;
import com.capybee.engine.rules.Condition;
import com.capybee.engine.rules.CounterDef;
import com.capybee.engine.rules.Effect;
import com.capybee.engine.rules.ObjectiveDef;
import com.capybee.engine.rules.Rules;
import com.capybee.engine.rules.SetDef;
import com.capybee.engine.content.json.SceneSets;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RuleReferenceValidator implements SectionValidator {
    @Override
    public void validate(Pack pack, PackValidationContext context) {
        Rules rules = RulesParser.parse(pack);
        Map<String, CounterDef> counters = new LinkedHashMap<>();
        rules.counters().forEach(def -> counters.put(def.id(), def));
        Map<String, SetDef> sets = new LinkedHashMap<>();
        rules.sets().forEach(def -> sets.put(def.id(), def));

        Set<String> emitted = new HashSet<>();
        for (ObjectiveDef objective : rules.objectives()) {
            validateCondition(objective.completeWhen(), counters, sets, context, "rules.json", "/objectives/" + objective.id() + "/completeWhen");
            validateEffects(objective.onComplete(), counters, sets, context, "rules.json", "/objectives/" + objective.id() + "/onComplete", emitted);
        }
        for (Map.Entry<LocaleTag, SceneSet> localeEntry : SceneSets.load(pack).entrySet()) {
            for (Scene scene : localeEntry.getValue().scenes().values()) {
                String scenePointer = "/scenes/" + scene.id();
                validateEffects(scene.onEnter(), counters, sets, context, sceneFile(pack, localeEntry.getKey()), scenePointer + "/onEnter", emitted);
                validateConditionList(scene.branches().stream().map(branch -> branch.condition()).toList(),
                        counters, sets, context, sceneFile(pack, localeEntry.getKey()), scenePointer + "/branches");
                for (SceneOption option : scene.options()) {
                    String optionPointer = scenePointer + "/options/" + option.id();
                    validateCondition(option.requires(), counters, sets, context, sceneFile(pack, localeEntry.getKey()), optionPointer + "/requires");
                    validateEffects(option.effects(), counters, sets, context, sceneFile(pack, localeEntry.getKey()), optionPointer + "/effects", emitted);
                }
            }
        }
        for (EventDef event : pack.manifest().events()) {
            if (!emitted.contains(event.id())) context.warn("EVENT_NEVER_EMITTED", "pack.json", "/events", "Declared event is never emitted: " + event.id());
        }
    }

    private void validateConditionList(List<Condition> conditions, Map<String, CounterDef> counters,
                                      Map<String, SetDef> sets, PackValidationContext context,
                                      String file, String pointer) {
        for (int index = 0; index < conditions.size(); index++) {
            validateCondition(conditions.get(index), counters, sets, context, file, pointer + "/" + index + "/if");
        }
    }

    private void validateCondition(Condition condition, Map<String, CounterDef> counters, Map<String, SetDef> sets,
                                   PackValidationContext context, String file, String pointer) {
        if (condition == null) return;
        if (condition instanceof Condition.CounterAtLeast item) validateCounterValue(item.counter(), item.value(), counters, context, file, pointer);
        else if (condition instanceof Condition.CounterAtMost item) validateCounterValue(item.counter(), item.value(), counters, context, file, pointer);
        else if (condition instanceof Condition.CounterEquals item) validateCounterValue(item.counter(), item.value(), counters, context, file, pointer);
        else if (condition instanceof Condition.SetContains item) validateSetValue(item.set(), item.value(), sets, context, file, pointer);
        else if (condition instanceof Condition.SetSizeAtLeast item) {
            SetDef def = sets.get(item.set());
            if (def != null && item.size() > def.values().size()) {
                context.error("CONDITION_INVALID", file, pointer, "Set size threshold exceeds its allowed values");
            }
        } else if (condition instanceof Condition.All item) {
            item.conditions().forEach(child -> validateCondition(child, counters, sets, context, file, pointer));
        } else if (condition instanceof Condition.Any item) {
            item.conditions().forEach(child -> validateCondition(child, counters, sets, context, file, pointer));
        } else if (condition instanceof Condition.Not item) {
            validateCondition(item.condition(), counters, sets, context, file, pointer);
        }
    }

    private void validateCounterValue(String id, int value, Map<String, CounterDef> counters,
                                      PackValidationContext context, String file, String pointer) {
        CounterDef def = counters.get(id);
        if (def != null && (value < def.min() || value > def.max())) {
            context.error("CONDITION_INVALID", file, pointer, "Counter comparison is outside its declared range");
        }
    }

    private void validateSetValue(String id, String value, Map<String, SetDef> sets,
                                  PackValidationContext context, String file, String pointer) {
        SetDef def = sets.get(id);
        if (def != null && !def.values().contains(value)) {
            context.error("CONDITION_INVALID", file, pointer, "Set member is not declared");
        }
    }

    private void validateEffects(List<Effect> effects, Map<String, CounterDef> counters, Map<String, SetDef> sets,
                                 PackValidationContext context, String file, String pointer, Set<String> emitted) {
        for (int index = 0; index < effects.size(); index++) {
            Effect effect = effects.get(index);
            String effectPointer = pointer + "/" + index;
            if (effect instanceof Effect.CounterAdd item) {
                CounterDef def = counters.get(item.counter());
                if (def != null && def.maxDelta() != null && Math.abs((long) item.delta()) > def.maxDelta()) {
                    context.error("EFFECT_INVALID", file, effectPointer + "/delta", "Counter delta exceeds maxDelta");
                }
            } else if (effect instanceof Effect.CounterSet item) {
                CounterDef def = counters.get(item.counter());
                if (def != null && (item.value() < def.min() || item.value() > def.max())) {
                    context.error("EFFECT_INVALID", file, effectPointer + "/value", "Counter value is outside its declared range");
                }
            } else if (effect instanceof Effect.SetAdd item) {
                validateEffectSetValue(item.set(), item.value(), sets, context, file, effectPointer);
            } else if (effect instanceof Effect.SetRemove item) {
                validateEffectSetValue(item.set(), item.value(), sets, context, file, effectPointer);
            } else if (effect instanceof Effect.EmitEvent item) {
                emitted.add(item.event());
            }
        }
    }

    private void validateEffectSetValue(String setId, String value, Map<String, SetDef> sets,
                                        PackValidationContext context, String file, String pointer) {
        SetDef def = sets.get(setId);
        if (def != null && !def.values().contains(value)) {
            context.error("EFFECT_INVALID", file, pointer + "/value", "Set member is not declared");
        }
    }

    private String sceneFile(Pack pack, LocaleTag locale) {
        return pack.manifest().files().stream().filter(file -> file.startsWith("scenes/" + locale.value() + "/"))
                .findFirst().orElse("pack.json");
    }
}