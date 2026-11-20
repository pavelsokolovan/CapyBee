package com.capybee.engine.places;

import com.capybee.engine.content.Scene;
import com.capybee.engine.rules.Effect;

import java.util.LinkedHashSet;
import java.util.Set;

/** Potential scene edges for static validation. MOVE still checks its live conditions. */
public final class PlaceEntryGraph {
    private PlaceEntryGraph() {}

    public static Set<String> targets(PlaceMap map, Scene scene) {
        if (map == null || scene.end()
                || map.navigation() != null && !map.navigation().allowedSceneIds().contains(scene.id())) {
            return Set.of();
        }
        // A scene may inherit its place from the preceding scene or an option effect.
        String place = null;
        for (Effect effect : scene.onEnter()) {
            if (effect instanceof Effect.GoTo move) place = move.place();
        }

        Set<String> destinations = new LinkedHashSet<>();
        for (PlaceLinkDef link : map.links()) {
            if (place == null || place.equals(link.from())) destinations.add(link.to());
            if (link.bidirectional() && (place == null || place.equals(link.to()))) destinations.add(link.from());
        }
        Set<String> targets = new LinkedHashSet<>();
        for (PlaceDef destination : map.places()) {
            if (destination.entry() != null && destinations.contains(destination.id())) {
                targets.add(destination.entry().sceneId());
            }
        }
        return targets;
    }
}
