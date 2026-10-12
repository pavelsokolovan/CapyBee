package com.capybee.engine.api;

import java.util.Map;

public record Event(String id, Map<String, String> payload) {
    public Event {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
