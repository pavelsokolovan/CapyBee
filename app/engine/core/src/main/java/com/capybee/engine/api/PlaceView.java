package com.capybee.engine.api;

public record PlaceView(String id, String label, String description, Integer x, Integer y, String moveBehavior, String moveHint) {
    public PlaceView(String id, String label, String description, Integer x, Integer y) {
        this(id, label, description, x, y, null, null);
    }
}
