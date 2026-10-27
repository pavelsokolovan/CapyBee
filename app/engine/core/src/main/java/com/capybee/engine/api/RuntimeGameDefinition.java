package com.capybee.engine.api;

import com.capybee.engine.content.SceneSet;
import com.capybee.engine.pack.ContentPolicy;
import com.capybee.engine.pack.EventDef;
import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.StartInputDef;
import com.capybee.engine.places.PlaceMap;
import com.capybee.engine.rules.Rules;

import java.util.List;
import java.util.Map;

/** Trusted, immutable execution input. It is deliberately not a persisted domain schema. */
interface RuntimeGameDefinition {
    LocaleTag locale();
    SceneSet scenes();
    Rules rules();
    Map<String, String> strings();
    ContentPolicy contentPolicy();
    List<EventDef> events();
    List<StartInputDef> startInputs();
    PlaceMap places();
    PackRef snapshotPack();
    boolean presentationReady();

    default SnapshotCapability snapshotCapability() {
        return SnapshotCapability.SUPPORTED;
    }
}
