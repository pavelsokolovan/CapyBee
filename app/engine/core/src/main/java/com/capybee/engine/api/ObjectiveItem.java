package com.capybee.engine.api;

public record ObjectiveItem(String id, String label, String description, ObjectiveState state) {
    public ObjectiveItem(String id, String label, ObjectiveState state) {
        this(id, label, null, state);
    }
}
