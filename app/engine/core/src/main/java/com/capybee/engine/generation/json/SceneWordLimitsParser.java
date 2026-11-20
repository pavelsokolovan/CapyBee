package com.capybee.engine.generation.json;

import com.capybee.engine.generation.SceneWordLimits;
import tools.jackson.databind.JsonNode;

import java.util.Set;

public final class SceneWordLimitsParser {
    public static final String FIELD = "sceneWordLimits";
    private static final Set<String> KEYS = Set.of("maxBlockWords", "minChoiceWords", "maxChoiceWords");

    private SceneWordLimitsParser() {}

    public static SceneWordLimits parse(JsonNode policy) {
        if (policy == null || !policy.isObject()) throw new IllegalArgumentException("SCENE_WORD_LIMITS_INVALID");
        JsonNode limits = policy.get(FIELD);
        if (limits == null) return null;
        if (!limits.isObject() || !limits.propertyNames().equals(KEYS)) {
            throw new IllegalArgumentException("SCENE_WORD_LIMITS_INVALID");
        }
        return new SceneWordLimits(integer(limits, "maxBlockWords"), integer(limits, "minChoiceWords"),
                integer(limits, "maxChoiceWords"));
    }

    private static int integer(JsonNode limits, String key) {
        JsonNode value = limits.get(key);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IllegalArgumentException("SCENE_WORD_LIMITS_INVALID");
        }
        return value.intValue();
    }
}
