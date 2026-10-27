package com.capybee.engine.api;

import com.capybee.engine.generation.blueprint.DynamicGameBundle;
import com.capybee.engine.generation.blueprint.DynamicPresentationArtifact;

import java.util.List;
import java.util.Objects;

/** Dynamic-only source data carried beside source-neutral runtime state. */
public record DynamicSnapshotSource(DynamicGameBundle bundle, List<DynamicPresentationArtifact> artifacts,
        int totalProviderCalls) {
    public DynamicSnapshotSource {
        Objects.requireNonNull(bundle);
        artifacts = List.copyOf(artifacts);
        if (totalProviderCalls < bundle.continuation().blueprintProviderCalls()
                || totalProviderCalls > bundle.continuation().constraints().totalCallsPerRun()) {
            throw new IllegalArgumentException("Invalid dynamic provider-call count");
        }
    }
}
