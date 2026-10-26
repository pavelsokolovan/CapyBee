package com.capybee.engine.generation.blueprint;

import com.capybee.engine.generation.GenerationArtifact;
import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.port.TextGenerationResult;

import java.util.List;
import java.util.Objects;

/** Immutable, redacted, definition-bound in-memory presentation evidence. */
public record DynamicPresentationArtifact(int artifactFormatVersion, String artifactId, String sceneId,
        LocaleTag locale, String policyVersion, GenerationArtifact.Outcome outcome,
        GenerationArtifact.PlannedScenePayload payload, List<String> continuityBasisSceneIds,
        Evidence evidence) {
    public static final int FORMAT_VERSION = 1;
    public DynamicPresentationArtifact {
        if (artifactFormatVersion != FORMAT_VERSION) throw new IllegalArgumentException("Unsupported dynamic artifact version");
        Objects.requireNonNull(artifactId); Objects.requireNonNull(sceneId); Objects.requireNonNull(locale);
        Objects.requireNonNull(policyVersion); Objects.requireNonNull(outcome); Objects.requireNonNull(payload);
        continuityBasisSceneIds = List.copyOf(continuityBasisSceneIds); Objects.requireNonNull(evidence);
    }
    @Override public String toString() {
        return "DynamicPresentationArtifact[version=" + artifactFormatVersion + ", sceneId=" + sceneId
                + ", outcome=" + outcome + ", content=redacted]";
    }
    public record Evidence(int providerCalls, String failureCode, TextGenerationResult.TokenUsage usage,
                           List<String> findingCodes) {
        public Evidence { findingCodes = findingCodes == null ? List.of() : List.copyOf(findingCodes); }
    }
}
