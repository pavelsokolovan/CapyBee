package com.capybee.engine.generation;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class GenerationQualityEvaluator {
    public static final int EVALUATOR_VERSION = 1;
    private static final Pattern UNSAFE_CONTROL = Pattern.compile("[\\u001B\\u202A-\\u202E\\u2066-\\u2069]");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{[^}]+}}|\\$\\{[^}]+}|<slot>", Pattern.CASE_INSENSITIVE);
    private static final Pattern META = Pattern.compile("\\b(system|developer) (prompt|message|instruction)|\\b(hidden rules|output schema|internal id)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRIVATE_REQUEST = Pattern.compile("\\b(password|credential|exact address|school name|phone number|email address)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MECHANIC = Pattern.compile("\\b(set|increment|decrement|add|remove|complete|fail|jump to|change) (the )?(counter|flag|objective|scene|phase|outcome|result)\\b", Pattern.CASE_INSENSITIVE);

    public QualityReport evaluate(GenerationCatalog.Stage stage, GenerationArtifact.Payload payload,
                                  QualityContext context, GenerationQualityProfile profile) {
        List<QualityFinding> findings = new ArrayList<>();
        List<Field> fields = fields(payload);
        for (Field field : fields) {
            String value = field.value();
            if (UNSAFE_CONTROL.matcher(value).find()) block(findings, "Q_OUTPUT_UNSAFE_CONTROL", field.scope(), Map.of());
            if (PLACEHOLDER.matcher(value).find()) block(findings, "Q_OUTPUT_UNRESOLVED_PLACEHOLDER", field.scope(), Map.of());
            if (META.matcher(value).find()) block(findings, "Q_OUTPUT_META_INSTRUCTION", field.scope(), Map.of());
            if (PRIVATE_REQUEST.matcher(value).find()) block(findings, "Q_OUTPUT_PRIVATE_DATA_REQUEST", field.scope(), Map.of());
            if (MECHANIC.matcher(value).find()) block(findings, "Q_CONTROL_MECHANIC_CLAIM", field.scope(), Map.of());
            readability(field, profile, findings);
        }
        duplicateRules(payload, fields, context, profile, findings);
        phraseRules(stage, fields, context, profile, findings);
        findings.sort(java.util.Comparator.comparing(QualityFinding::severity).reversed()
                .thenComparing(QualityFinding::ruleId).thenComparing(QualityFinding::scope));
        QualityDecision decision = findings.stream().anyMatch(f -> f.severity() == FindingSeverity.BLOCKING)
                ? QualityDecision.REJECT : findings.isEmpty() ? QualityDecision.ACCEPT : QualityDecision.ACCEPT_WITH_ADVISORIES;
        return new QualityReport(decision, findings, new QualityMetrics(fields.size()));
    }

    private static void readability(Field field, GenerationQualityProfile profile, List<QualityFinding> findings) {
        var rules = profile.readability();
        for (String paragraph : field.value().split("\\R", -1)) if (paragraph.codePointCount(0, paragraph.length()) > rules.maxParagraphCodePoints())
            block(findings, "Q_READABILITY_PARAGRAPH_EXTREME", field.scope(), Map.of("codePoints", paragraph.codePointCount(0, paragraph.length())));
        for (String sentence : field.value().split("(?<=[.!?])\\s+")) if (sentence.codePointCount(0, sentence.length()) > rules.maxSentenceCodePoints())
            block(findings, "Q_READABILITY_SENTENCE_EXTREME", field.scope(), Map.of("codePoints", sentence.codePointCount(0, sentence.length())));
        long letters = field.value().codePoints().filter(Character::isLetter).count();
        long upper = field.value().codePoints().filter(Character::isUpperCase).count();
        int ratio = letters == 0 ? 0 : (int) (upper * 10_000 / letters);
        if (letters >= 8 && ratio > rules.maxUppercaseRatioBasisPoints()) block(findings, "Q_READABILITY_UPPERCASE_EXTREME", field.scope(), Map.of("basisPoints", ratio));
    }

    private static void duplicateRules(GenerationArtifact.Payload payload, List<Field> fields, QualityContext context,
                                       GenerationQualityProfile profile, List<QualityFinding> findings) {
        if (payload instanceof GenerationArtifact.PlannedScenePayload scene) {
            for (int i = 0; i < scene.choices().size(); i++) for (int j = i + 1; j < scene.choices().size(); j++) {
                int score = similarity(scene.choices().get(i).label(), scene.choices().get(j).label());
                if (score >= profile.repetition().choiceThresholdBasisPoints()) block(findings, "Q_CHOICE_LABEL_AMBIGUOUS", "choices", Map.of("similarityBasisPoints", score));
            }
            for (AcceptedSceneText recent : context.recentScenes()) {
                int score = similarity(scene.narration(), recent.narration());
                if (score >= profile.repetition().recentSceneThresholdBasisPoints()) block(findings, "Q_SCENE_RECENT_DUPLICATE", "scene.narration", Map.of("similarityBasisPoints", score));
            }
        }
        for (int i = 0; i < fields.size(); i++) for (int j = i + 1; j < fields.size(); j++) {
            if (!fields.get(i).comparable() || !fields.get(j).comparable()) continue;
            int score = similarity(fields.get(i).value(), fields.get(j).value());
            if (score >= profile.repetition().fieldDuplicateThresholdBasisPoints())
                block(findings, "Q_FIELD_DUPLICATE", fields.get(j).scope(), Map.of("similarityBasisPoints", score));
        }
    }

    private static void phraseRules(GenerationCatalog.Stage stage, List<Field> fields, QualityContext context,
                                    GenerationQualityProfile profile, List<QualityFinding> findings) {
        List<String> completion = profile.phraseSets().getOrDefault("completionClaims", List.of());
        List<String> prohibited = profile.phraseSets().getOrDefault("forbiddenAudienceTerms", List.of());
        for (Field field : fields) {
            if (!context.ending() && matches(field.value(), completion)) block(findings, "Q_SCENE_EARLY_COMPLETION", field.scope(), Map.of());
            if (matches(field.value(), prohibited)) block(findings, "Q_OUTPUT_AUDIENCE_PROHIBITED", field.scope(), Map.of());
            for (String tone : context.toneTags()) {
                var toneProfile = profile.toneProfiles().get(tone);
                if (toneProfile == null) continue;
                for (String set : toneProfile.incompatiblePhraseSets()) if (matches(field.value(), profile.phraseSets().getOrDefault(set, List.of())))
                    block(findings, "Q_TONE_INCOMPATIBLE", field.scope(), Map.of());
                for (String set : toneProfile.advisoryPhraseSets()) if (matches(field.value(), profile.phraseSets().getOrDefault(set, List.of())))
                    findings.add(new QualityFinding("Q_TONE_ADVISORY", FindingSeverity.ADVISORY, field.scope(), Map.of()));
            }
            for (var truth : profile.truthPhrases()) if (context.activeTruthFactIds().contains(truth.factId()) && matches(field.value(), truth.contradicting()))
                block(findings, stage == GenerationCatalog.Stage.SCENE ? "Q_SCENE_CONTINUITY_CONTRADICTION" : "Q_STATE_CONTRADICTION", field.scope(), Map.of());
        }
    }

    public static String normalize(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return normalized.codePoints().mapToObj(cp -> Character.isLetterOrDigit(cp) ? new String(Character.toChars(cp)) : " ")
                .collect(java.util.stream.Collectors.joining()).trim().replaceAll("\\s+", " ");
    }
    public static int similarity(String left, String right) {
        Set<String> a = shingles(normalize(left)); Set<String> b = shingles(normalize(right));
        if (a.isEmpty() && b.isEmpty()) return 10_000;
        Set<String> union = new HashSet<>(a); union.addAll(b); Set<String> intersection = new HashSet<>(a); intersection.retainAll(b);
        return union.isEmpty() ? 0 : intersection.size() * 10_000 / union.size();
    }
    private static Set<String> shingles(String value) {
        String[] tokens = value.isBlank() ? new String[0] : value.split(" "); Set<String> result = new HashSet<>();
        if (tokens.length < 2) { java.util.Collections.addAll(result, tokens); return result; }
        for (int i = 0; i + 1 < tokens.length; i++) result.add(tokens[i] + "\u0000" + tokens[i + 1]); return result;
    }
    private static boolean matches(String text, List<String> phrases) { String n = " " + normalize(text) + " "; return phrases.stream().map(GenerationQualityEvaluator::normalize).anyMatch(p -> !p.isBlank() && n.contains(" " + p + " ")); }
    private static void block(List<QualityFinding> findings, String id, String scope, Map<String,Integer> measures) { findings.add(new QualityFinding(id, FindingSeverity.BLOCKING, scope, measures)); }

    private static List<Field> fields(GenerationArtifact.Payload payload) {
        List<Field> out = new ArrayList<>();
        if (payload instanceof GenerationArtifact.ThemePayload p) { out.add(new Field("theme.title",p.title(),true)); out.add(new Field("theme.premise",p.premise(),true)); }
        else if (payload instanceof GenerationArtifact.StoryPlanPayload p) { out.add(new Field("plan.title",p.title(),true)); out.add(new Field("plan.premise",p.premise(),true)); out.add(new Field("plan.setting",p.setting(),true)); for (var x:p.phases()){out.add(new Field("phase:"+x.phaseId()+".goal",x.goal(),true));out.add(new Field("phase:"+x.phaseId()+".development",x.development(),true));out.add(new Field("phase:"+x.phaseId()+".transition",x.transition(),true));}}
        else if (payload instanceof GenerationArtifact.ScenarioPayload p) { out.add(new Field("scenario.title",p.title(),true)); out.add(new Field("scenario.premise",p.premise(),true)); out.add(new Field("scenario.setting",p.setting(),true)); }
        else if (payload instanceof GenerationArtifact.CastPayload p) for(var x:p.characters()) out.add(new Field("cast:"+x.slotId()+".description",x.description(),true));
        else if (payload instanceof GenerationArtifact.QuestPayload p) for(var x:p.objectives()){out.add(new Field("quest:"+x.objectiveId()+".title",x.title(),true));out.add(new Field("quest:"+x.objectiveId()+".description",x.description(),true));}
        else if (payload instanceof GenerationArtifact.ScenePayload p) { out.add(new Field("scene.title",p.title(),true));out.add(new Field("scene.narration",p.narration(),true));for(var x:p.dialogue())out.add(new Field("dialogue",x.text(),true));for(var x:p.choices())out.add(new Field("choices",x.label(),false));}
        else if (payload instanceof GenerationArtifact.PlannedScenePayload p) { out.add(new Field("scene.title",p.title(),true));out.add(new Field("scene.narration",p.narration(),true));for(var x:p.dialogue())out.add(new Field("dialogue",x.text(),true));for(var x:p.choices())out.add(new Field("choices",x.label(),false));for(var x:p.continuityFacts())out.add(new Field("continuityFacts",x,true));if(p.ending()!=null){out.add(new Field("ending.headline",p.ending().headline(),true));out.add(new Field("ending.summary",p.ending().summary(),true));for(var x:p.ending().objectiveNotes())out.add(new Field("ending.objectiveNotes",x.text(),true));out.add(new Field("ending.reflection",p.ending().reflection(),true));}}
        return out;
    }
    private record Field(String scope, String value, boolean comparable) {}

    public record QualityContext(List<String> toneTags, List<AcceptedSceneText> recentScenes,
                                 Set<String> activeTruthFactIds, boolean ending) {
        public QualityContext { toneTags=List.copyOf(toneTags);recentScenes=List.copyOf(recentScenes);activeTruthFactIds=Set.copyOf(activeTruthFactIds); }
    }
    public record AcceptedSceneText(String title, String narration) {}
    public record QualityReport(QualityDecision decision, List<QualityFinding> findings, QualityMetrics metrics) { public QualityReport { findings=List.copyOf(findings); } }
    public record QualityFinding(String ruleId, FindingSeverity severity, String scope, Map<String,Integer> measures) { public QualityFinding { measures=Map.copyOf(new LinkedHashMap<>(measures)); } }
    public record QualityMetrics(int fieldCount) {}
    public enum QualityDecision { ACCEPT, ACCEPT_WITH_ADVISORIES, REJECT }
    public enum FindingSeverity { BLOCKING, ADVISORY }
}
