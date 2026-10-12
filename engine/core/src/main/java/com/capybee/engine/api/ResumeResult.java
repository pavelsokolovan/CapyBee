package com.capybee.engine.api;

import java.util.List;

public record ResumeResult(Run run, List<Issue> issues) {
    public boolean ok() {
        return run != null && issues.stream().noneMatch(issue -> issue.severity() == Issue.Severity.ERROR);
    }
}
