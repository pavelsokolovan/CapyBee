package com.capybee.engine.session.wire;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

public final class SessionJson {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SessionJson() {}

    public static String encode(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("SESSION_JSON_ENCODE_FAILED");
        }
    }

    public static <T> T decode(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("SESSION_JSON_DECODE_FAILED");
        }
    }

    public static JsonNode tree(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("SESSION_JSON_DECODE_FAILED");
        }
    }

    public static <T> T convert(JsonNode node, Class<T> type) {
        try {
            return MAPPER.readValue(MAPPER.writeValueAsString(node), type);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("SESSION_JSON_DECODE_FAILED");
        }
    }
}
