package com.capybee.engine.generation;

import com.capybee.engine.pack.LocaleTag;

import java.util.List;
import java.util.Map;

public record GenerationQualityProfile(
        int schemaVersion, String profileId, LocaleTag locale,
        ReadabilityRules readability, RepetitionRules repetition,
        Map<String, List<String>> phraseSets, Map<String, ToneProfile> toneProfiles,
        List<TruthPhraseFamily> truthPhrases, String fingerprint) {
    public GenerationQualityProfile {
        Map<String, List<String>> sortedPhrases = new java.util.LinkedHashMap<>();
        phraseSets.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sortedPhrases.put(entry.getKey(), List.copyOf(entry.getValue())));
        phraseSets = java.util.Collections.unmodifiableMap(sortedPhrases);
        Map<String, ToneProfile> sortedTones = new java.util.LinkedHashMap<>();
        toneProfiles.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sortedTones.put(entry.getKey(), entry.getValue()));
        toneProfiles = java.util.Collections.unmodifiableMap(sortedTones);
        truthPhrases = List.copyOf(truthPhrases);
    }

    public record ReadabilityRules(int maxSentenceCodePoints, int maxParagraphCodePoints,
                                   int maxConsecutiveShortSentences, int maxUppercaseRatioBasisPoints) {}
    public record RepetitionRules(int fieldDuplicateThresholdBasisPoints, int recentSceneThresholdBasisPoints,
                                  int choiceThresholdBasisPoints, int recentSceneWindow) {}
    public record ToneProfile(List<String> incompatiblePhraseSets, List<String> advisoryPhraseSets) {
        public ToneProfile { incompatiblePhraseSets = List.copyOf(incompatiblePhraseSets); advisoryPhraseSets = List.copyOf(advisoryPhraseSets); }
    }
    public record TruthPhraseFamily(String factId, List<String> affirming, List<String> contradicting) {
        public TruthPhraseFamily { affirming = List.copyOf(affirming); contradicting = List.copyOf(contradicting); }
    }
}
