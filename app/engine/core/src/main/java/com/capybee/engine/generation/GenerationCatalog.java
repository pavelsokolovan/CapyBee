package com.capybee.engine.generation;

import java.util.List;
import java.util.Objects;

public record GenerationCatalog(
        int schemaVersion,
        List<String> supportedLocales,
        String defaultLocale,
        List<StageDefinition> stages,
        List<TemplateDefinition> templates,
        List<SceneSlot> sceneSlots,
        GenerationLimits limits) {

    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    public GenerationCatalog {
        supportedLocales = List.copyOf(supportedLocales);
        stages = List.copyOf(stages);
        templates = List.copyOf(templates);
        sceneSlots = List.copyOf(sceneSlots);
        Objects.requireNonNull(defaultLocale, "defaultLocale");
        Objects.requireNonNull(limits, "limits");
    }

    public record TemplateDefinition(String id, Stage stage, List<String> actorSlots,
                                     List<String> objectiveIds, List<String> toneTags) {
        public TemplateDefinition {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(stage, "stage");
            actorSlots = List.copyOf(actorSlots);
            objectiveIds = List.copyOf(objectiveIds);
            toneTags = List.copyOf(toneTags);
        }
    }

    public record StageDefinition(
            Stage stage,
            String templateId,
            List<Stage> dependencies,
            String fallbackResource) {
        public StageDefinition {
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(templateId, "templateId");
            dependencies = List.copyOf(dependencies);
            Objects.requireNonNull(fallbackResource, "fallbackResource");
        }
    }

    public record SceneSlot(
            String slotId,
            String sceneId,
            String templateId,
            String fallbackResource,
            List<String> choiceIds) {
        public SceneSlot {
            Objects.requireNonNull(slotId, "slotId");
            Objects.requireNonNull(sceneId, "sceneId");
            Objects.requireNonNull(templateId, "templateId");
            Objects.requireNonNull(fallbackResource, "fallbackResource");
            choiceIds = List.copyOf(choiceIds);
        }
    }

    public record GenerationLimits(
            int totalCallsPerRun,
            int callsPerStage,
            int maxOutputTokens,
            int maxInputCharacters,
            int repairAttempts) {
        public GenerationLimits {
            if (totalCallsPerRun < 1 || totalCallsPerRun > 64
                    || callsPerStage < 1 || callsPerStage > 4
                    || maxOutputTokens < 1 || maxOutputTokens > 1_500
                    || maxInputCharacters < 1 || maxInputCharacters > 32_000
                    || repairAttempts < 0 || repairAttempts > 1) {
                throw new IllegalArgumentException("Generation limits are outside supported bounds");
            }
        }

        public static GenerationLimits defaults() {
            return new GenerationLimits(16, 2, 800, 12_000, 1);
        }
    }

    public enum Stage { THEME, SCENARIO, CAST, QUEST, SCENE }
}