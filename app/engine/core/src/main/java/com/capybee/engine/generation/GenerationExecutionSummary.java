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
            TextGenerationResult.TokenUsage usage,
            GenerationQualityEvaluator.QualityDecision qualityDecision,
            int blockingFindingCount,
            int advisoryFindingCount,
            List<String> findingCodes) {
        public StageExecution(GenerationCatalog.Stage stage, GenerationArtifact.Outcome outcome, int providerCalls,
                              long elapsedMillis, String providerId, String failureCode,
                              TextGenerationResult.TokenUsage usage) {
            this(stage, outcome, providerCalls, elapsedMillis, providerId, failureCode, usage,
                    null, 0, 0, List.of());
        }
        public StageExecution { findingCodes = findingCodes == null ? List.of() : List.copyOf(findingCodes); }
    }
}
