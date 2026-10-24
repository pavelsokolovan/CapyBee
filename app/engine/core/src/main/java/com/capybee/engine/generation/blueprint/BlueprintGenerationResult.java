package com.capybee.engine.generation.blueprint;

import com.capybee.engine.generation.profile.ProfileIdentity;
import java.util.List;

public sealed interface BlueprintGenerationResult permits BlueprintGenerationResult.CandidateReviewed,
        BlueprintGenerationResult.ReviewPending, BlueprintGenerationResult.NeedsInputAction,
        BlueprintGenerationResult.GenerationFailed, BlueprintGenerationResult.Cancelled,
        BlueprintGenerationResult.InProgress {
    record Evidence(ProfileIdentity profile, String preparationId, int attempts, String reviewerVersion,
                    String validatorVersion) {}
    record CandidateReviewed(ParsedGameBlueprint blueprint, Evidence evidence) implements BlueprintGenerationResult {
        @Override public String toString(){return "CandidateReviewed[blueprint=redacted, evidence="+evidence+"]";}
    }
    record ReviewPending(ParsedGameBlueprint blueprint, Evidence evidence, String code) implements BlueprintGenerationResult {
        @Override public String toString(){return "ReviewPending[blueprint=redacted, evidence="+evidence+", code="+code+"]";}
    }
    record NeedsInputAction(String code, List<Recovery> recovery) implements BlueprintGenerationResult {
        public NeedsInputAction { recovery=List.copyOf(recovery); }
    }
    record GenerationFailed(String code, List<Recovery> recovery, FallbackOffer fallback) implements BlueprintGenerationResult {
        public GenerationFailed { recovery=List.copyOf(recovery); }
    }
    record Cancelled(String code) implements BlueprintGenerationResult {}
    record InProgress(String code) implements BlueprintGenerationResult {}
    record FallbackOffer(String packId, String packVersion, String locale, String lengthPresetId,
                         String challengePresetId, String disclosure) {}
    enum Recovery { EDIT_AND_RECONFIRM, RETRY_AND_RECONFIRM, SKIP_SUGGESTION_AND_RECONFIRM, CANCEL, SELECT_FALLBACK }
}
