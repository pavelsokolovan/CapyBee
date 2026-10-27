package com.capybee.engine.generation.blueprint;

import com.capybee.engine.generation.profile.EffectiveGenerationConstraints;
import com.capybee.engine.generation.profile.ProfileIdentity;
import com.capybee.engine.pack.LocaleTag;

import java.util.Objects;

/** Safe immutable policy required to continue unresolved presentation after resume. */
public record DynamicPresentationContinuation(int continuationFormatVersion, ProfileIdentity profileIdentity,
        LocaleTag locale, String policyVersion, EffectiveGenerationConstraints constraints,
        int sceneMaxAttempts, String qualityResource, int blueprintProviderCalls) {
    public static final int FORMAT_VERSION = 1;

    public DynamicPresentationContinuation {
        if (continuationFormatVersion != FORMAT_VERSION) throw new IllegalArgumentException("Unsupported continuation version");
        Objects.requireNonNull(profileIdentity); Objects.requireNonNull(locale); Objects.requireNonNull(policyVersion);
        Objects.requireNonNull(constraints);
        qualityResource = qualityResource == null ? "" : qualityResource;
        if (sceneMaxAttempts < 1 || sceneMaxAttempts > 2 || blueprintProviderCalls < 1
                || blueprintProviderCalls > constraints.totalCallsPerRun()) {
            throw new IllegalArgumentException("Invalid continuation budget");
        }
    }
}
