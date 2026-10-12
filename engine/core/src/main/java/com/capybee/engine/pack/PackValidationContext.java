package com.capybee.engine.pack;

import java.util.ArrayList;
import java.util.List;

public final class PackValidationContext {
    private final List<PackIssue> issues = new ArrayList<>();
    private final ReferenceIndex references = new ReferenceIndex();

    public void error(String code, String file, String pointer, String message) {
        issues.add(new PackIssue(PackIssue.Severity.ERROR, code, file, pointer, message));
    }

    public void warn(String code, String file, String pointer, String message) {
        issues.add(new PackIssue(PackIssue.Severity.WARNING, code, file, pointer, message));
    }

    public ReferenceIndex references() { return references; }
    public List<PackIssue> issues() { return List.copyOf(issues); }
    public boolean hasErrors() {
        return issues.stream().anyMatch(issue -> issue.severity() == PackIssue.Severity.ERROR);
    }
}