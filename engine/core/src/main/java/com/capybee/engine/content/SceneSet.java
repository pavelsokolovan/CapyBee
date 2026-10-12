package com.capybee.engine.content;

import com.capybee.engine.pack.LocaleTag;

import java.util.Map;

public record SceneSet(LocaleTag locale, Map<String, Scene> scenes, String startId) {
    public SceneSet {
        scenes = Map.copyOf(scenes);
    }
}
