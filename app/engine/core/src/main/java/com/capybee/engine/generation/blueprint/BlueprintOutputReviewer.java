package com.capybee.engine.generation.blueprint;

import com.capybee.engine.generation.profile.ResolvedGamePreferences;
import java.util.List;

@FunctionalInterface
public interface BlueprintOutputReviewer {
    Review review(ParsedGameBlueprint blueprint, ResolvedGamePreferences preferences, String qualityPolicyJson);

    record Review(Status status, String policyVersion, List<BlueprintIssue> issues) {
        public Review { issues = issues == null ? List.of() : List.copyOf(issues); }
        public static Review pass(String version) { return new Review(Status.PASS, version, List.of()); }
        public static Review unavailable(String version) { return new Review(Status.UNAVAILABLE, version, List.of()); }
    }
    enum Status { PASS, REJECT, UNAVAILABLE }
}
