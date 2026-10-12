package com.capybee.engine.rules;

import java.util.List;

public record SetDef(String id, List<String> values, List<String> initial) {
    public SetDef {
        values = List.copyOf(values);
        initial = initial == null ? List.of() : List.copyOf(initial);
    }
}