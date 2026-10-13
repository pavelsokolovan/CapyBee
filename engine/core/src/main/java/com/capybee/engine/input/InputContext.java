package com.capybee.engine.input;

import com.capybee.engine.api.InputMode;

import java.util.List;
import java.util.Set;

public record InputContext(String localeTag, InputMode mode, int maxChars, List<InputOption> options,
                           String audience, Set<String> forbiddenTags, String policyVersion) {
    public InputContext {
        options = List.copyOf(options);
        forbiddenTags = Set.copyOf(forbiddenTags);
    }

    public record InputOption(String id, String label, boolean enabled) {}
}