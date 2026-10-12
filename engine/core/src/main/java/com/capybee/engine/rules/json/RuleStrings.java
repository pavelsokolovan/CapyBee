package com.capybee.engine.rules.json;

import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.Pack;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

public final class RuleStrings {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RuleStrings() {}

    public static Map<String, String> load(Pack pack, LocaleTag locale) {
        String file = "strings/" + locale.value() + ".json";
        try {
            JsonNode root = MAPPER.readTree(pack.requireFile(file));
            Map<String, String> values = new LinkedHashMap<>();
            if (root != null && root.isObject()) {
                root.properties().forEach(entry -> {
                    if (entry.getValue().isString()) values.put(entry.getKey(), entry.getValue().asString());
                });
            }
            return Map.copyOf(values);
        } catch (Exception exception) {
            return Map.of();
        }
    }
}