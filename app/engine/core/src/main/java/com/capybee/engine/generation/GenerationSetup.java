package com.capybee.engine.generation;

import java.util.List;
import java.util.Objects;

/** Localized presentation and initial-generation routing for declared start inputs. */
public record GenerationSetup(int schemaVersion, List<SetupQuestion> questions) {
    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    public GenerationSetup {
        questions = List.copyOf(questions);
    }

    public record SetupQuestion(String inputId, String helpKey, List<GenerationCatalog.Stage> stages,
                                List<String> defaultValues, List<SetupOption> options,
                                String freeTextNoticeKey) {
        public SetupQuestion {
            Objects.requireNonNull(inputId, "inputId");
            Objects.requireNonNull(helpKey, "helpKey");
            stages = List.copyOf(stages);
            defaultValues = List.copyOf(defaultValues);
            options = List.copyOf(options);
        }
    }

    public record SetupOption(String value, String labelKey) {
        public SetupOption {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(labelKey, "labelKey");
        }
    }
}
