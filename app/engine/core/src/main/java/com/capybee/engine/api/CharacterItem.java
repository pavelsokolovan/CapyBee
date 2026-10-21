package com.capybee.engine.api;

public record CharacterItem(String id, String slotId, String name, String description,
                            boolean inCurrentScene) {}
