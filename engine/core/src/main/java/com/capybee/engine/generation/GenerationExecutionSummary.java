package com.capybee.engine.generation;

import com.capybee.engine.port.TextGenerationResult;

import java.util.List;

public record GenerationExecutionSummary(List<StageExecution> stages, int totalProviderCalls) {
    public GenerationExecutionSummary { stages = List.copyOf(stages); }

    public record StageExecution(
            GenerationCatalog.Stage stage,
            GenerationArtifact.Outcome outcome,
            int providerCalls,
            long elapsedMillis,
            String providerId,
            String failureCode,
            TextGenerationResult.TokenUsage usage) {}
}