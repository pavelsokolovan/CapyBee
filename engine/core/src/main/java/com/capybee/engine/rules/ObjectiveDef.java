package com.capybee.engine.rules;

import java.util.List;

public record ObjectiveDef(String id, String labelKey, Condition completeWhen, List<Effect> onComplete) {
    public ObjectiveDef {
        onComplete = onComplete == null ? List.of() : List.copyOf(onComplete);
    }
}