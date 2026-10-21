package com.capybee.engine.rules;

public record OutcomeDef(String id, ResultClass resultClass, String labelKey) {
    public OutcomeDef(String id) {
        this(id, ResultClass.PARTIAL_SUCCESS, null);
    }
}
