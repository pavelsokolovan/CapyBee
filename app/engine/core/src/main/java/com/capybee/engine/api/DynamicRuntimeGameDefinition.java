package com.capybee.engine.api;

import com.capybee.engine.content.SceneSet;
import com.capybee.engine.generation.blueprint.MaterializedGameDefinition;
import com.capybee.engine.pack.ContentPolicy;
import com.capybee.engine.pack.EventDef;
import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.StartInputDef;
import com.capybee.engine.places.PlaceMap;
import com.capybee.engine.rules.Rules;

import java.util.List;
import java.util.Map;

record DynamicRuntimeGameDefinition(MaterializedGameDefinition materialized) implements RuntimeGameDefinition {
    DynamicRuntimeGameDefinition {
        if (materialized == null) throw new IllegalArgumentException("Dynamic definition is missing");
    }

    @Override public LocaleTag locale() { return materialized.locale(); }
    @Override public SceneSet scenes() { return materialized.runtimeSceneSet(); }
    @Override public Rules rules() { return materialized.runtimeRules(); }
    @Override public Map<String, String> strings() { return materialized.localizedContent(); }
    @Override public ContentPolicy contentPolicy() { return null; }
    @Override public List<EventDef> events() { return List.of(); }
    @Override public List<StartInputDef> startInputs() { return List.of(); }
    @Override public PlaceMap places() { return null; }
    @Override public PackRef snapshotPack() { return null; }
    @Override public boolean presentationReady() { return true; }
}
