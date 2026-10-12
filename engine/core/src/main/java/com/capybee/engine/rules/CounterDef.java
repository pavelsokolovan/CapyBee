package com.capybee.engine.rules;

public record CounterDef(String id, String labelKey, int min, int max, int initial,
                         boolean display, Integer maxDelta) {}