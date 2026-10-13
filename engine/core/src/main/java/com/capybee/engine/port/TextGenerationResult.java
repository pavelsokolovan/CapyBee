package com.capybee.engine.port;

import java.util.Objects;

public record TextGenerationResult(
        String outputJson,
        TokenUsage usage,
        String modelId,
        FinishReason finishReason) {

    public TextGenerationResult {
        Objects.requireNonNull(outputJson, "outputJson");
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(finishReason, "finishReason");
        if (modelId.isBlank()) throw new IllegalArgumentException("modelId must not be blank");
    }

    @Override
    public String toString() {
        return "TextGenerationResult[outputJson=redacted, usage=" + usage
                + ", modelId=" + modelId + ", finishReason=" + finishReason + "]";
    }

    public record TokenUsage(long inputTokens, long outputTokens, long cacheReadTokens, long cacheWriteTokens) {
        public TokenUsage {
            if (inputTokens < 0 || outputTokens < 0 || cacheReadTokens < 0 || cacheWriteTokens < 0) {
                throw new IllegalArgumentException("Token usage values must be nonnegative");
            }
        }
    }

    public enum FinishReason { COMPLETE, OUTPUT_LIMIT, REFUSED }
}