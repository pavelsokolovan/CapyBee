package com.capybee.engine.generation;

import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.PackId;
import com.capybee.engine.pack.PackVersion;

import java.util.List;
import java.util.Objects;

public record GenerationArtifact(
        int artifactSchemaVersion,
        PackId packId,
        PackVersion packVersion,
        LocaleTag locale,
        GenerationCatalog.Stage stage,
        String templateId,
        String artifactId,
        Outcome outcome,
        Payload payload,
        QualityStamp qualityStamp) {

    public static final int SUPPORTED_SCHEMA_VERSION = 3;

    public GenerationArtifact(int artifactSchemaVersion, PackId packId, PackVersion packVersion, LocaleTag locale,
                              GenerationCatalog.Stage stage, String templateId, String artifactId, Outcome outcome,
                              Payload payload) {
        this(artifactSchemaVersion, packId, packVersion, locale, stage, templateId, artifactId, outcome, payload, null);
    }

    public GenerationArtifact {
        if (artifactSchemaVersion < 1 || artifactSchemaVersion > SUPPORTED_SCHEMA_VERSION) throw new IllegalArgumentException("Unsupported artifact version");
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(packVersion, "packVersion");
        Objects.requireNonNull(locale, "locale");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(templateId, "templateId");
        Objects.requireNonNull(artifactId, "artifactId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(payload, "payload");
        if (artifactSchemaVersion == 3 && qualityStamp == null) throw new IllegalArgumentException("Quality stamp required");
        if (artifactSchemaVersion < 3 && qualityStamp != null) throw new IllegalArgumentException("Quality stamp unsupported");
    }

    @Override
    public String toString() {
        return "GenerationArtifact[version=" + artifactSchemaVersion + ", stage=" + stage
                + ", artifactId=" + artifactId + ", outcome=" + outcome + ", payload=redacted]";
    }

    public sealed interface Payload permits ThemePayload, ScenarioPayload, StoryPlanPayload, CastPayload,
            QuestPayload, ScenePayload, PlannedScenePayload {}

    public record ThemePayload(String title, String premise, List<String> toneTags) implements Payload {
        public ThemePayload { toneTags = List.copyOf(toneTags); }
    }

    public record ScenarioPayload(String title, String premise, String setting, List<String> beats) implements Payload {
        public ScenarioPayload { beats = List.copyOf(beats); }
    }

    public record StoryPlanPayload(String title, String premise, String setting, List<String> themes,
                                   List<String> storyPromises, List<PhasePlan> phases) implements Payload {
        public StoryPlanPayload {
            themes = List.copyOf(themes); storyPromises = List.copyOf(storyPromises); phases = List.copyOf(phases);
        }
    }

    public record PhasePlan(String phaseId, String goal, String development, String transition,
                            List<String> actorSlots, List<String> objectiveIds, List<String> toneTags,
                            List<String> storyRoles) {
        public PhasePlan {
            actorSlots = List.copyOf(actorSlots); objectiveIds = List.copyOf(objectiveIds);
            toneTags = List.copyOf(toneTags); storyRoles = List.copyOf(storyRoles);
        }
    }

    public record CastPayload(List<CastMember> characters) implements Payload {
        public CastPayload { characters = List.copyOf(characters); }
    }

    public record CastMember(String slotId, String actorId, String name, String description) {}

    public record QuestPayload(List<Quest> objectives) implements Payload {
        public QuestPayload { objectives = List.copyOf(objectives); }
    }

    public record Quest(String objectiveId, String title, String description) {}

    public record ScenePayload(String sceneId, String title, String narration, List<Dialogue> dialogue,
                               List<ChoiceLabel> choices) implements Payload {
        public ScenePayload {
            dialogue = List.copyOf(dialogue);
            choices = List.copyOf(choices);
        }
    }

    public record PlannedScenePayload(String sceneId, String title, String narration, List<PlannedDialogue> dialogue,
                                      List<ChoiceLabel> choices, List<String> continuityFacts,
                                      EndingNarrative ending) implements Payload {
        public PlannedScenePayload(String sceneId, String title, String narration, List<PlannedDialogue> dialogue,
                                   List<ChoiceLabel> choices, List<String> continuityFacts) {
            this(sceneId, title, narration, dialogue, choices, continuityFacts, null);
        }
        public PlannedScenePayload {
            dialogue = List.copyOf(dialogue); choices = List.copyOf(choices);
            continuityFacts = List.copyOf(continuityFacts);
        }
    }

    public record EndingNarrative(String headline, String summary, List<ObjectiveNote> objectiveNotes,
                                  String reflection) {
        public EndingNarrative { objectiveNotes = List.copyOf(objectiveNotes); }
    }

    public record ObjectiveNote(String objectiveId, String text) {}

    public record Dialogue(String actorId, String text) {}

    public record PlannedDialogue(String actorSlot, String text) {}

    public record ChoiceLabel(String id, String label) {}

    public enum Outcome { GENERATED, REPAIRED, FALLBACK }

    public record QualityStamp(int evaluatorVersion, String profileId, String profileFingerprint,
                               GenerationQualityEvaluator.QualityDecision decision,
                               List<String> advisoryCodes, String reportFingerprint) {
        public QualityStamp {
            if (evaluatorVersion < 1 || profileId == null || profileFingerprint == null || decision == null
                    || reportFingerprint == null || advisoryCodes == null || advisoryCodes.size() > 16)
                throw new IllegalArgumentException("Invalid quality stamp");
            advisoryCodes = advisoryCodes.stream().distinct().sorted().toList();
        }
    }
}
