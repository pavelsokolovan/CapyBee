package com.capybee.engine.api;

import com.capybee.engine.content.json.SceneSectionValidator;
import com.capybee.engine.pack.PackRegistry;
import com.capybee.engine.pack.SectionValidator;
import com.capybee.engine.rules.json.RulesSectionValidator;
import com.capybee.engine.rules.json.RuleReferenceValidator;

import java.util.List;

public interface Engine {
    static EngineBuilder builder(PackRegistry packs) {
        return new EngineBuilder(packs);
    }

    static List<SectionValidator> sectionValidators() {
        return List.of(new SceneSectionValidator(), new RulesSectionValidator(), new RuleReferenceValidator());
    }

    StartResult start(RunRequest request);

    ResumeResult resume(RunSnapshot snapshot);
}
