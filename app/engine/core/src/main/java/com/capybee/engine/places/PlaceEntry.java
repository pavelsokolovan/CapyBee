package com.capybee.engine.places;

import com.capybee.engine.rules.Condition;

public record PlaceEntry(String sceneId, Condition requires, String unmetHintKey) {}
