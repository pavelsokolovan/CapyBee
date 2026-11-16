package com.capybee.engine.places;

public record PlaceDef(String id, String labelKey, String descriptionKey, PlacePosition position, PlaceEntry entry) {
    public PlaceDef(String id, String labelKey, String descriptionKey, PlacePosition position) {
        this(id, labelKey, descriptionKey, position, null);
    }
}
