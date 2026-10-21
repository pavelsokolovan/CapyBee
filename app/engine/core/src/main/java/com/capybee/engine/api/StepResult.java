package com.capybee.engine.api;

import java.util.List;

public record StepResult(boolean accepted, View view, List<Event> events, List<Issue> issues) {
    public StepResult {
        events = events == null ? List.of() : List.copyOf(events);
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
