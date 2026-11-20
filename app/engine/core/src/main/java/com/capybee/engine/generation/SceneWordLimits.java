package com.capybee.engine.generation;

import com.capybee.engine.generation.GenerationArtifact.ChoiceLabel;
import com.capybee.engine.generation.GenerationQualityEvaluator.FindingSeverity;
import com.capybee.engine.generation.GenerationQualityEvaluator.QualityFinding;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Locale policy for prose that appears in the scene and its action buttons. */
public record SceneWordLimits(int maxBlockWords, int minChoiceWords, int maxChoiceWords) {
    public static final String BLOCK_RULE = "Q_READABILITY_SCENE_BLOCK_WORD_LIMIT";
    public static final String CHOICE_RULE = "Q_READABILITY_CHOICE_WORD_RANGE";
    private static final String RUN = "\\p{M}*+[\\p{L}\\p{Nd}][\\p{L}\\p{M}\\p{Nd}]*";
    private static final Pattern WORD = Pattern.compile("(?<![\\p{L}\\p{M}\\p{Nd}])" + RUN + "(?:['’‐‑-]" + RUN + ")*");

    public SceneWordLimits {
        if (maxBlockWords < 1 || maxBlockWords > 100 || minChoiceWords < 1
                || maxChoiceWords > 100 || minChoiceWords > maxChoiceWords) {
            throw new IllegalArgumentException("SCENE_WORD_LIMITS_INVALID");
        }
    }

    public static int wordCount(String text) {
        return (int) WORD.matcher(Normalizer.normalize(text, Normalizer.Form.NFC)).results().count();
    }

    public boolean allowsBlock(int words) {
        return words <= maxBlockWords;
    }

    public boolean allowsChoice(int words) {
        return words >= minChoiceWords && words <= maxChoiceWords;
    }

    public String sceneInstructions() {
        return "Use up to " + maxBlockWords + " words in the entire narration and in each dialogue line. "
                + "Each choice label must have " + minChoiceWords + "–" + maxChoiceWords + " words. "
                + "Line breaks do not reset the count. Standalone icons and punctuation do not count.";
    }

    public String blueprintInstructions() {
        return "Each scene intent must have at most " + maxBlockWords + " words and each choice intent must have "
                + minChoiceWords + "–" + maxChoiceWords + " words. Write both as final player-facing copy "
                + "because the fallback displays them verbatim. Standalone icons and punctuation do not count.";
    }

    public List<QualityFinding> evaluate(String narration, List<String> dialogue, List<ChoiceLabel> choices) {
        List<QualityFinding> findings = new ArrayList<>();
        checkBlock(findings, "scene.narration", narration);
        for (int i = 0; i < dialogue.size(); i++) {
            checkBlock(findings, "scene.dialogue[" + i + "].text", dialogue.get(i));
        }
        for (int i = 0; i < choices.size(); i++) {
            int words = wordCount(choices.get(i).label());
            if (!allowsChoice(words)) {
                findings.add(new QualityFinding(CHOICE_RULE, FindingSeverity.BLOCKING,
                        "scene.choices[" + i + "].label",
                        Map.of("words", words, "minWords", minChoiceWords, "maxWords", maxChoiceWords)));
            }
        }
        return findings;
    }

    private void checkBlock(List<QualityFinding> findings, String scope, String text) {
        int words = wordCount(text);
        if (!allowsBlock(words)) {
            findings.add(new QualityFinding(BLOCK_RULE, FindingSeverity.BLOCKING, scope,
                    Map.of("words", words, "maxWords", maxBlockWords)));
        }
    }
}
