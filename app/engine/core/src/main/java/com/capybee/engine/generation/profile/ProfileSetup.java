package com.capybee.engine.generation.profile;

import com.capybee.engine.generation.GenerationSetup.SetupOption;
import com.capybee.engine.pack.StartInputDef;
import java.util.List;

public record ProfileSetup(int schemaVersion, List<SetupStep> steps, List<StartInputDef> inputs,
                           List<SetupQuestion> questions) {
    public ProfileSetup { steps = List.copyOf(steps); inputs = List.copyOf(inputs); questions = List.copyOf(questions); }
    public record SetupStep(String id, String titleKey, List<String> inputIds) {
        public SetupStep { inputIds = List.copyOf(inputIds); }
    }
    public record SetupQuestion(String inputId, String helpKey, List<String> defaultValues,
                                List<SetupOption> options, String freeTextNoticeKey) {
        public SetupQuestion { defaultValues = List.copyOf(defaultValues); options = List.copyOf(options); }
    }
}
