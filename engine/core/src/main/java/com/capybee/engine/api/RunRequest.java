package com.capybee.engine.api;

import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.PackId;

import java.util.Map;

public record RunRequest(PackId packId, LocaleTag locale, Map<String, String> startInputs, Long seed) {
    public RunRequest {
        startInputs = startInputs == null ? Map.of() : Map.copyOf(startInputs);
    }
}
