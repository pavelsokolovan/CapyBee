package com.capybee.engine.api;

import com.capybee.engine.generation.blueprint.BlueprintGenerationRequest;

import java.util.Objects;

/** Confirmed, validated preparation plus the optional runtime seed. */
public record DynamicGameBootstrapRequest(BlueprintGenerationRequest preparation, Long seed) {
    public DynamicGameBootstrapRequest { Objects.requireNonNull(preparation, "preparation"); }
    @Override public String toString() {
        return "DynamicGameBootstrapRequest[preparation=redacted, seed=" + seed + "]";
    }
}
