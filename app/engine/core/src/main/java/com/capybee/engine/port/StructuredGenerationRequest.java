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
        String slotKey,
        Purpose purpose) {

    public StructuredGenerationRequest(String requestId, String taskId, String systemPolicyVersion,
                                       String taskInstructions, String untrustedDataJson,
                                       String outputSchemaJson, int maxOutputTokens, CostClass costClass) {
        this(requestId, taskId, systemPolicyVersion, taskInstructions, untrustedDataJson,
                outputSchemaJson, maxOutputTokens, costClass, null, Purpose.PRESENTATION);
    }

    public StructuredGenerationRequest(String requestId, String taskId, String systemPolicyVersion,
                                       String taskInstructions, String untrustedDataJson,
                                       String outputSchemaJson, int maxOutputTokens, CostClass costClass,
                                       String slotKey) {
        this(requestId, taskId, systemPolicyVersion, taskInstructions, untrustedDataJson,
                outputSchemaJson, maxOutputTokens, costClass, slotKey, Purpose.PRESENTATION);
    }

    public StructuredGenerationRequest {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(systemPolicyVersion, "systemPolicyVersion");
        Objects.requireNonNull(taskInstructions, "taskInstructions");
        Objects.requireNonNull(untrustedDataJson, "untrustedDataJson");
        Objects.requireNonNull(outputSchemaJson, "outputSchemaJson");
        Objects.requireNonNull(costClass, "costClass");
        Objects.requireNonNull(purpose, "purpose");
        if (requestId.isBlank() || taskId.isBlank() || systemPolicyVersion.isBlank()
                || taskInstructions.isBlank() || untrustedDataJson.isBlank() || outputSchemaJson.isBlank()) {
            throw new IllegalArgumentException("Generation request fields must not be blank");
        }
        int minimum = purpose == Purpose.GAME_BLUEPRINT ? 512 : 1;
        int maximum = purpose == Purpose.GAME_BLUEPRINT ? 12_000 : 1_500;
        if (maxOutputTokens < minimum || maxOutputTokens > maximum) {
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
    public enum Purpose { PRESENTATION, GAME_BLUEPRINT }
}
