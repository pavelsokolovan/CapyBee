package com.capybee.engine.pack.json;

import com.capybee.engine.pack.PackValidationContext;
import tools.jackson.databind.JsonNode;

final class StringsParser {
    private StringsParser() {}

    static void validate(JsonNode root, String locale, String file, PackValidationContext context) {
        if (root == null) return;
        if (!root.isObject()) {
            context.error("PACK_STRINGS_INVALID", file, "", "Expected a flat string map");
            return;
        }
        for (var entry : root.properties()) {
            String pointer = "/" + JsonDocuments.pointerToken(entry.getKey());
            JsonNode value = entry.getValue();
            if (!ManifestParser.validKey(entry.getKey()) || !value.isString()
                    || value.asString().isBlank() || value.asString().length() > 400) {
                context.error("PACK_STRINGS_INVALID", file, pointer, "Invalid string key or value");
            } else {
                context.references().define("stringKey:" + locale, entry.getKey(), file, pointer);
            }
        }
    }
}