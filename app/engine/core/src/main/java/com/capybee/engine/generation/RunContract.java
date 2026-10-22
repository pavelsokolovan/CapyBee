package com.capybee.engine.generation;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record RunContract(RunContractKind kind, int contractVersion, List<RunPhase> phases,
                          List<String> utilitySceneIds, RunLength length, CompletionContract completion,
                          String setupResource, Map<String, String> qualityResources) {
    public RunContract(RunContractKind kind, int contractVersion, List<RunPhase> phases,
                       List<String> utilitySceneIds, RunLength length, CompletionContract completion) {
        this(kind, contractVersion, phases, utilitySceneIds, length, completion, null, Map.of());
    }
    public RunContract(RunContractKind kind, int contractVersion, List<RunPhase> phases,
                       List<String> utilitySceneIds, RunLength length, CompletionContract completion,
                       String setupResource) {
        this(kind, contractVersion, phases, utilitySceneIds, length, completion, setupResource, Map.of());
    }
    public RunContract {
        Objects.requireNonNull(kind);
        phases = List.copyOf(phases);
        utilitySceneIds = List.copyOf(utilitySceneIds);
        Objects.requireNonNull(length);
        Objects.requireNonNull(completion);
        qualityResources = Map.copyOf(qualityResources);
    }
}
