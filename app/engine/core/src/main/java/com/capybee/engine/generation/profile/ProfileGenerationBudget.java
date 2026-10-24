package com.capybee.engine.generation.profile;

public record ProfileGenerationBudget(int totalCallsPerRun, int blueprintMaxAttempts,
        int blueprintMaxOutputTokens, int sceneMaxAttempts, int sceneMaxOutputTokens,
        int maxInputCharacters) {}
