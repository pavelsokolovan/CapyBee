package com.capybee.engine.generation;

import java.util.List;
import java.util.Objects;

public record RunContract(RunContractKind kind, int contractVersion, List<RunPhase> phases,
                          List<String> utilitySceneIds, RunLength length, CompletionContract completion,
                          String setupResource) {
    public RunContract(RunContractKind kind, int contractVersion, List<RunPhase> phases,
                       List<String> utilitySceneIds, RunLength length, CompletionContract completion) {
        this(kind, contractVersion, phases, utilitySceneIds, length, completion, null);
    }
    public RunContract {
        Objects.requireNonNull(kind);
        phases = List.copyOf(phases);
        utilitySceneIds = List.copyOf(utilitySceneIds);
        Objects.requireNonNull(length);
        Objects.requireNonNull(completion);
    }
}
