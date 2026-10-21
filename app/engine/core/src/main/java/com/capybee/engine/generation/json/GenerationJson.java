package com.capybee.engine.generation.json;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public final class GenerationJson {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GenerationJson() {}

    public static String encode(Object value) {
        try { return MAPPER.writeValueAsString(value); }
        catch (JacksonException exception) { throw new IllegalStateException("GENERATION_CONTEXT_INVALID"); }
    }
}