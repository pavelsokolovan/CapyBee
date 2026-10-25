package com.capybee.engine.generation.blueprint;

import com.capybee.engine.generation.profile.EffectiveGenerationConstraints;
import com.capybee.engine.generation.profile.ResolvedGamePreferences;
import com.capybee.engine.generation.profile.ValidatedGeneratorProfile;
import java.util.List;

@FunctionalInterface
public interface BlueprintSemanticValidator {
    Validation validate(ParsedGameBlueprint blueprint, ValidatedGeneratorProfile profile,
                        ResolvedGamePreferences preferences, EffectiveGenerationConstraints constraints);

    record Validation(Status status, String validatorVersion, List<BlueprintIssue> issues,
                      SemanticallyValidatedBlueprint validatedBlueprint) {
        public Validation {
            issues = issues == null ? List.of() : List.copyOf(issues);
            if ((status == Status.PASS) != (validatedBlueprint != null)) {
                throw new IllegalArgumentException("Semantic PASS must carry validated evidence");
            }
        }
        public static Validation unavailable() { return new Validation(Status.UNAVAILABLE, null, List.of(), null); }
    }
    enum Status { PASS, REJECT, INCONCLUSIVE, UNAVAILABLE, INVALID_CONTEXT }
}
