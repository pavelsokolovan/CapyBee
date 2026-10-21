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
        GenerationLimits limits,
        RunContract runContract) {

    public static final int SUPPORTED_SCHEMA_VERSION = 2;

    public GenerationCatalog(int schemaVersion, List<String> supportedLocales, String defaultLocale,
                             List<StageDefinition> stages, List<TemplateDefinition> templates,
                             List<SceneSlot> sceneSlots, GenerationLimits limits) {
        this(schemaVersion, supportedLocales, defaultLocale, stages, templates, sceneSlots, limits, null);
    }

    public GenerationCatalog {
        supportedLocales = List.copyOf(supportedLocales);
        stages = List.copyOf(stages);
        templates = List.copyOf(templates);
        sceneSlots = List.copyOf(sceneSlots);
        Objects.requireNonNull(defaultLocale, "defaultLocale");
        Objects.requireNonNull(limits, "limits");
    }

    public record TemplateDefinition(String id, Stage stage, List<String> actorSlots,
                                     List<String> objectiveIds, List<String> toneTags,
                                     List<String> storyRoles) {
        public TemplateDefinition(String id, Stage stage, List<String> actorSlots,
                                  List<String> objectiveIds, List<String> toneTags) {
            this(id, stage, actorSlots, objectiveIds, toneTags, List.of());
        }
        public TemplateDefinition {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(stage, "stage");
            actorSlots = List.copyOf(actorSlots);
            objectiveIds = List.copyOf(objectiveIds);
            toneTags = List.copyOf(toneTags);
            storyRoles = List.copyOf(storyRoles);
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
            List<String> choiceIds,
            String phaseId,
            String storyRole,
            List<String> actorSlots,
            List<String> objectiveIds,
            List<String> toneTags) {
        public SceneSlot(String slotId, String sceneId, String templateId, String fallbackResource,
                         List<String> choiceIds) {
            this(slotId, sceneId, templateId, fallbackResource, choiceIds, null, null,
                    List.of(), List.of(), List.of());
        }
        public SceneSlot {
            Objects.requireNonNull(slotId, "slotId");
            Objects.requireNonNull(sceneId, "sceneId");
            Objects.requireNonNull(templateId, "templateId");
            Objects.requireNonNull(fallbackResource, "fallbackResource");
            choiceIds = List.copyOf(choiceIds);
            actorSlots = List.copyOf(actorSlots);
            objectiveIds = List.copyOf(objectiveIds);
            toneTags = List.copyOf(toneTags);
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
