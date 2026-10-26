package com.capybee.engine.api;

import com.capybee.engine.content.SceneSet;
import com.capybee.engine.pack.*;
import com.capybee.engine.places.PlaceMap;
import com.capybee.engine.rules.Rules;
import com.capybee.engine.rules.json.RuleStrings;

import java.util.List;
import java.util.Map;

record PackRuntimeGameDefinition(Pack pack, LocaleTag locale, SceneSet scenes, Rules rules,
                                 PlaceMap places) implements RuntimeGameDefinition {
    PackRuntimeGameDefinition {
        if (pack == null || locale == null || scenes == null || rules == null) {
            throw new IllegalArgumentException("Pack runtime definition is incomplete");
        }
    }

    @Override public Map<String, String> strings() { return RuleStrings.load(pack, locale); }
    @Override public ContentPolicy contentPolicy() { return pack.manifest().contentPolicy(); }
    @Override public List<EventDef> events() { return pack.manifest().events(); }
    @Override public List<StartInputDef> startInputs() { return pack.manifest().startInputs(); }
    @Override public PackRef snapshotPack() { return new PackRef(pack.manifest().id(), pack.manifest().version()); }
    @Override public boolean presentationReady() { return true; }
}
