package com.capybee.engine.generation.blueprint;

import com.capybee.engine.generation.profile.EffectiveGenerationConstraints;
import java.util.List;

@FunctionalInterface
public interface BlueprintSemanticValidator {
    Validation validate(ParsedGameBlueprint blueprint, EffectiveGenerationConstraints constraints);

    record Validation(Status status, String validatorVersion, List<BlueprintIssue> issues) {
        public Validation { issues = issues == null ? List.of() : List.copyOf(issues); }
        public static Validation pass(String version) { return new Validation(Status.PASS, version, List.of()); }
        public static Validation unavailable() { return new Validation(Status.UNAVAILABLE, null, List.of()); }
    }
    enum Status { PASS, REJECT, INCONCLUSIVE, UNAVAILABLE }
}
