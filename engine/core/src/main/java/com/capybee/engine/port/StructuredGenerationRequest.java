package com.capybee.engine.port;

import java.util.Objects;

public record StructuredGenerationRequest(
        String requestId,
        String taskId,
        String systemPolicyVersion,
        String taskInstructions,
        String untrustedDataJson,
        String outputSchemaJson,
        int maxOutputTokens,
        CostClass costClass,
        String slotKey) {

    public StructuredGenerationRequest(String requestId, String taskId, String systemPolicyVersion,
                                       String taskInstructions, String untrustedDataJson,
                                       String outputSchemaJson, int maxOutputTokens, CostClass costClass) {
        this(requestId, taskId, systemPolicyVersion, taskInstructions, untrustedDataJson,
                outputSchemaJson, maxOutputTokens, costClass, null);
    }

    public StructuredGenerationRequest {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(systemPolicyVersion, "systemPolicyVersion");
        Objects.requireNonNull(taskInstructions, "taskInstructions");
        Objects.requireNonNull(untrustedDataJson, "untrustedDataJson");
        Objects.requireNonNull(outputSchemaJson, "outputSchemaJson");
        Objects.requireNonNull(costClass, "costClass");
        if (requestId.isBlank() || taskId.isBlank() || systemPolicyVersion.isBlank()
                || taskInstructions.isBlank() || untrustedDataJson.isBlank() || outputSchemaJson.isBlank()) {
            throw new IllegalArgumentException("Generation request fields must not be blank");
        }
        if (maxOutputTokens < 1 || maxOutputTokens > 1_500) {
            throw new IllegalArgumentException("Output token limit is outside the supported range");
        }
        if (slotKey != null && (slotKey.isBlank() || slotKey.length() > 200)) {
            throw new IllegalArgumentException("Generation slot key is invalid");
        }
    }

    @Override
    public String toString() {
        return "StructuredGenerationRequest[redacted]";
    }

    public enum CostClass { LIGHT, STANDARD }
}