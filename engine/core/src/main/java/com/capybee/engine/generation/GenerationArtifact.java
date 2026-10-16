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
        Payload payload) {

    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    public GenerationArtifact {
        if (artifactSchemaVersion != SUPPORTED_SCHEMA_VERSION) throw new IllegalArgumentException("Unsupported artifact version");
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(packVersion, "packVersion");
        Objects.requireNonNull(locale, "locale");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(templateId, "templateId");
        Objects.requireNonNull(artifactId, "artifactId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(payload, "payload");
    }

    @Override
    public String toString() {
        return "GenerationArtifact[version=" + artifactSchemaVersion + ", stage=" + stage
                + ", artifactId=" + artifactId + ", outcome=" + outcome + ", payload=redacted]";
    }

    public sealed interface Payload permits ThemePayload, ScenarioPayload, CastPayload, QuestPayload, ScenePayload {}

    public record ThemePayload(String title, String premise, List<String> toneTags) implements Payload {
        public ThemePayload { toneTags = List.copyOf(toneTags); }
    }

    public record ScenarioPayload(String title, String premise, String setting, List<String> beats) implements Payload {
        public ScenarioPayload { beats = List.copyOf(beats); }
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

    public record Dialogue(String actorId, String text) {}

    public record ChoiceLabel(String id, String label) {}

    public enum Outcome { GENERATED, REPAIRED, FALLBACK }
}