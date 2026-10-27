package com.capybee.engine.generation.blueprint;

import com.capybee.engine.generation.profile.ProfileIdentity;
import com.capybee.engine.pack.LocaleTag;

import java.util.Objects;

/** Canonical, accepted blueprint provenance. Contains no provider request or response metadata. */
public record AcceptedBlueprintArchive(int archiveFormatVersion, String canonicalBlueprint,
        ProfileIdentity profileIdentity, LocaleTag locale, String lengthPresetId, String challengePresetId,
        String semanticContractVersion, String validatorVersion, String reviewerVersion,
        int providerAttempts, String blueprintFingerprint) {
    public static final int FORMAT_VERSION = 1;

    public AcceptedBlueprintArchive {
        if (archiveFormatVersion != FORMAT_VERSION) throw new IllegalArgumentException("Unsupported blueprint archive version");
        Objects.requireNonNull(canonicalBlueprint); Objects.requireNonNull(profileIdentity); Objects.requireNonNull(locale);
        Objects.requireNonNull(lengthPresetId); Objects.requireNonNull(challengePresetId);
        Objects.requireNonNull(semanticContractVersion); Objects.requireNonNull(validatorVersion);
        Objects.requireNonNull(reviewerVersion); Objects.requireNonNull(blueprintFingerprint);
        if (providerAttempts < 1) throw new IllegalArgumentException("Invalid blueprint attempt count");
    }

    @Override public String toString() {
        return "AcceptedBlueprintArchive[version=" + archiveFormatVersion + ", fingerprint="
                + blueprintFingerprint + ", content=redacted]";
    }
}
