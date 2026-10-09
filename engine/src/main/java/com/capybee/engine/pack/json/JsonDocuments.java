package com.capybee.engine.pack.json;

import com.capybee.engine.pack.PackValidationContext;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class JsonDocuments {
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private JsonDocuments() {}

    static JsonNode parse(String text, String file, PackValidationContext context) {
        try {
            return MAPPER.readTree(text);
        } catch (JacksonException exception) {
            var location = exception.getLocation();
            context.error("PACK_JSON_INVALID", file, "", "Invalid JSON at line "
                    + (location == null ? 1 : location.getLineNr()) + ", column "
                    + (location == null ? 1 : location.getColumnNr()));
            return null;
        }
    }

    static String pointerToken(String key) {
        return key.replace("~", "~0").replace("/", "~1");
    }
}