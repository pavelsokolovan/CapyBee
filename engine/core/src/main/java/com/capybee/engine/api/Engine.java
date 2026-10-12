package com.capybee.engine.api;

import com.capybee.engine.content.json.SceneSectionValidator;
import com.capybee.engine.pack.PackRegistry;
import com.capybee.engine.pack.SectionValidator;

import java.util.List;

public interface Engine {
    static EngineBuilder builder(PackRegistry packs) {
        return new EngineBuilder(packs);
    }

    static List<SectionValidator> sectionValidators() {
        return List.of(new SceneSectionValidator());
    }

    StartResult start(RunRequest request);

    ResumeResult resume(RunSnapshot snapshot);
}
