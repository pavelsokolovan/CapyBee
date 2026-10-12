package com.capybee.engine.api;

import java.util.List;

public record StartResult(Run run, List<Issue> issues, List<Event> events) {
    public StartResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
        events = events == null ? List.of() : List.copyOf(events);
    }

    public StartResult(Run run, List<Issue> issues) {
        this(run, issues, List.of());
    }

    public boolean ok() {
        return run != null && issues.stream().noneMatch(issue -> issue.severity() == Issue.Severity.ERROR);
    }
}
