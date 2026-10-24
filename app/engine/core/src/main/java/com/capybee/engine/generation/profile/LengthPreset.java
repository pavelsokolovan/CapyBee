package com.capybee.engine.generation.profile;

public record LengthPreset(String id, String labelKey, int minStoryTransitions,
                           int maxStoryTransitions, int maxScenes) {}
