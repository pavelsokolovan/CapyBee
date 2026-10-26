package com.capybee.engine.api;

import com.capybee.engine.content.json.SceneSectionValidator;
import com.capybee.engine.pack.PackRegistry;
import com.capybee.engine.pack.SectionValidator;
import com.capybee.engine.input.PreparedStartInputs;
import com.capybee.engine.places.json.PlacesSectionValidator;
import com.capybee.engine.rules.json.RulesSectionValidator;
import com.capybee.engine.rules.json.RuleReferenceValidator;

import java.util.List;

public interface Engine {
    static EngineBuilder builder(PackRegistry packs) {
        return new EngineBuilder(packs);
    }

    static List<SectionValidator> sectionValidators() {
        return List.of(new SceneSectionValidator(), new RulesSectionValidator(), new RuleReferenceValidator(),
            new PlacesSectionValidator());
    }

    StartResult start(RunRequest request);

    default DynamicGameBootstrapResult startDynamic(DynamicGameBootstrapRequest request) {
        return new DynamicGameBootstrapResult.Failed("DYNAMIC_BLUEPRINT_FAILED",
                List.of(new Issue(Issue.Severity.ERROR, "DYNAMIC_BLUEPRINT_FAILED",
                        "Dynamic game startup is not configured")));
    }

    default StartResult start(RunRequest request, PreparedStartInputs preparedInputs) {
        return start(request);
    }

    ResumeResult resume(RunSnapshot snapshot);
}
