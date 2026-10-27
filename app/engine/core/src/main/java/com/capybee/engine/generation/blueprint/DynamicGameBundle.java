package com.capybee.engine.generation.blueprint;

import java.util.Objects;

/** Immutable self-contained dynamic game definition and provenance. */
public record DynamicGameBundle(int bundleFormatVersion, AcceptedBlueprintArchive acceptedBlueprint,
        MaterializedGameDefinition definition, DynamicPresentationContinuation continuation,
        String definitionFingerprint, String continuationFingerprint, String gameFingerprint) {
    public static final int FORMAT_VERSION = 1;

    public DynamicGameBundle {
        if (bundleFormatVersion != FORMAT_VERSION) throw new IllegalArgumentException("Unsupported dynamic bundle version");
        Objects.requireNonNull(acceptedBlueprint); Objects.requireNonNull(definition); Objects.requireNonNull(continuation);
        Objects.requireNonNull(definitionFingerprint); Objects.requireNonNull(continuationFingerprint);
        Objects.requireNonNull(gameFingerprint);
    }

    @Override public String toString() {
        return "DynamicGameBundle[version=" + bundleFormatVersion + ", fingerprint=" + gameFingerprint
                + ", content=redacted]";
    }
}
