package com.capybee.engine.api;

public record ObjectiveResultItem(String id, String label, String description,
                                  ObjectiveState state, String note) {}
