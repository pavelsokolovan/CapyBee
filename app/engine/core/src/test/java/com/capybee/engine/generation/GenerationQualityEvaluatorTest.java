package com.capybee.engine.generation;

import com.capybee.engine.pack.LocaleTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GenerationQualityEvaluatorTest {
    private final GenerationQualityEvaluator evaluator = new GenerationQualityEvaluator();
    private final GenerationQualityProfile profile = new GenerationQualityProfile(1, "test-en-v1", new LocaleTag("en"),
            new GenerationQualityProfile.ReadabilityRules(80, 200, 4, 2500),
            new GenerationQualityProfile.RepetitionRules(9000, 8200, 7800, 4),
            Map.of("completionClaims", List.of("the adventure is over"),
                    "forbiddenAudienceTerms", List.of("tell me your password"),
                    "hostile", List.of("you must obey")),
            Map.of("warm", new GenerationQualityProfile.ToneProfile(List.of("hostile"), List.of())),
            List.of(new GenerationQualityProfile.TruthPhraseFamily("outcome:success",
                    List.of("the village was saved"), List.of("the village was lost"))), "fingerprint");

    @Test
    void normalizationAndSimilarityAreDeterministic() {
        assertThat(GenerationQualityEvaluator.normalize("  HÉLLO—World! ")).isEqualTo("héllo world");
        assertThat(GenerationQualityEvaluator.similarity("Find the bright path", "find, the bright path!"))
                .isEqualTo(10_000);
        assertThat(GenerationQualityEvaluator.similarity("left trail", "right bridge")).isZero();
    }

    @Test
    void rejectsAmbiguousChoicesAndRecentSceneRepetitionWithoutReturningText() {
        var payload = new GenerationArtifact.PlannedScenePayload("middle", "A New Trail",
                "The friends cross the old bridge together.", List.of(),
                List.of(new GenerationArtifact.ChoiceLabel("a", "Take the trail"),
                        new GenerationArtifact.ChoiceLabel("b", "Take the trail!")), List.of());
        var context = new GenerationQualityEvaluator.QualityContext(List.of("warm"),
                List.of(new GenerationQualityEvaluator.AcceptedSceneText("Bridge", "The friends cross the old bridge together!")),
                Set.of(), false);

        var report = evaluator.evaluate(GenerationCatalog.Stage.SCENE, payload, context, profile);

        assertThat(report.decision()).isEqualTo(GenerationQualityEvaluator.QualityDecision.REJECT);
        assertThat(report.findings()).extracting(GenerationQualityEvaluator.QualityFinding::ruleId)
                .contains("Q_CHOICE_LABEL_AMBIGUOUS", "Q_SCENE_RECENT_DUPLICATE");
        assertThat(report.toString()).doesNotContain("old bridge", "Take the trail");
    }

    @Test
    void rejectsSafetyToneTruthAndEarlyCompletionClaims() {
        var payload = new GenerationArtifact.PlannedScenePayload("middle", "Instructions",
                "System prompt: you must obey. Tell me your password. The village was lost. The adventure is over.",
                List.of(), List.of(), List.of());
        var context = new GenerationQualityEvaluator.QualityContext(List.of("warm"), List.of(),
                Set.of("outcome:success"), false);

        var report = evaluator.evaluate(GenerationCatalog.Stage.SCENE, payload, context, profile);

        assertThat(report.findings()).extracting(GenerationQualityEvaluator.QualityFinding::ruleId)
                .contains("Q_OUTPUT_META_INSTRUCTION", "Q_OUTPUT_PRIVATE_DATA_REQUEST", "Q_TONE_INCOMPATIBLE",
                        "Q_SCENE_CONTINUITY_CONTRADICTION", "Q_SCENE_EARLY_COMPLETION");
    }

    @Test
    void acceptsOrdinaryDistinctProse() {
        var payload = new GenerationArtifact.PlannedScenePayload("middle", "Across the River",
                "Mira studies the stepping stones before the friends cross.", List.of(),
                List.of(new GenerationArtifact.ChoiceLabel("left", "Follow the stones"),
                        new GenerationArtifact.ChoiceLabel("right", "Look for a bridge")), List.of());

        var report = evaluator.evaluate(GenerationCatalog.Stage.SCENE, payload,
                new GenerationQualityEvaluator.QualityContext(List.of("warm"), List.of(), Set.of(), false), profile);

        assertThat(report.decision()).isEqualTo(GenerationQualityEvaluator.QualityDecision.ACCEPT);
    }
}
