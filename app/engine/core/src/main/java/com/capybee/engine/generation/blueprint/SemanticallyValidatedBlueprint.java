package com.capybee.engine.generation.blueprint;

import com.capybee.engine.generation.profile.ProfileIdentity;
import com.capybee.engine.pack.LocaleTag;
import java.util.List;
import java.util.Objects;

/** A non-executable blueprint whose complete v1 semantic proof succeeded. */
public final class SemanticallyValidatedBlueprint {
    private final ParsedGameBlueprint blueprint;
    private final ProfileIdentity profileIdentity;
    private final LocaleTag locale;
    private final String lengthPresetId;
    private final String challengePresetId;
    private final Evidence evidence;
    private final ValidatedMechanicsPlan mechanicsPlan;

    SemanticallyValidatedBlueprint(ParsedGameBlueprint blueprint, ProfileIdentity profileIdentity,
                                   LocaleTag locale, String lengthPresetId, String challengePresetId,
                                   Evidence evidence, ValidatedMechanicsPlan mechanicsPlan) {
        this.blueprint = Objects.requireNonNull(blueprint);
        this.profileIdentity = Objects.requireNonNull(profileIdentity);
        this.locale = Objects.requireNonNull(locale);
        this.lengthPresetId = Objects.requireNonNull(lengthPresetId);
        this.challengePresetId = Objects.requireNonNull(challengePresetId);
        this.evidence = Objects.requireNonNull(evidence);
        this.mechanicsPlan = Objects.requireNonNull(mechanicsPlan);
        if (!evidence.semanticContractVersion().equals(mechanicsPlan.contractVersion()))
            throw new IllegalArgumentException("Generated mechanics contract mismatch");
    }

    public ParsedGameBlueprint blueprint() { return blueprint; }
    public ProfileIdentity profileIdentity() { return profileIdentity; }
    public LocaleTag locale() { return locale; }
    public String lengthPresetId() { return lengthPresetId; }
    public String challengePresetId() { return challengePresetId; }
    public Evidence evidence() { return evidence; }
    public ValidatedMechanicsPlan mechanicsPlan() { return mechanicsPlan; }

    @Override public String toString() { return "SemanticallyValidatedBlueprint[redacted]"; }

    public record Evidence(String semanticContractVersion, String validatorVersion, int sceneCount,
                           int edgeCount, int exploredConfigurationCount,
                           int minimumTransitions, int maximumTransitions,
                           List<String> witnessedEndingIds, List<String> witnessedObjectiveIds) {
        public Evidence {
            semanticContractVersion = Objects.requireNonNull(semanticContractVersion);
            validatorVersion = Objects.requireNonNull(validatorVersion);
            witnessedEndingIds = List.copyOf(witnessedEndingIds);
            witnessedObjectiveIds = List.copyOf(witnessedObjectiveIds);
        }

        @Override public String toString() {
            return "Evidence[semanticContractVersion=" + semanticContractVersion
                    + ", validatorVersion=" + validatorVersion + ", sceneCount=" + sceneCount
                    + ", edgeCount=" + edgeCount + ", exploredConfigurationCount="
                    + exploredConfigurationCount + ", minimumTransitions=" + minimumTransitions
                    + ", maximumTransitions=" + maximumTransitions + ", witnesses=redacted]";
        }
    }
}
