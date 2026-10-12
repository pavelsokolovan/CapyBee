package com.capybee.engine.pack;

import java.util.List;
import java.util.Set;

public record PackManifest(
        int schemaVersion, PackId id, PackVersion version, String name, String description,
        List<LocaleTag> locales, LocaleTag defaultLocale, Set<PackFeature> features,
        List<StartInputDef> startInputs, List<EventDef> events,
        ContentPolicy contentPolicy, List<String> files) {
    public PackManifest {
        locales = List.copyOf(locales);
        features = Set.copyOf(features);
        startInputs = List.copyOf(startInputs);
        events = List.copyOf(events);
        files = List.copyOf(files);
    }
}