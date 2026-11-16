package com.capybee.engine.places;

import java.util.List;

public record PlaceNavigation(List<String> allowedSceneIds, String unavailableHintKey) {
    public PlaceNavigation { allowedSceneIds = List.copyOf(allowedSceneIds); }
}
