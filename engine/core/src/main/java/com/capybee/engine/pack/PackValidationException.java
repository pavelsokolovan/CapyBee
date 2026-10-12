package com.capybee.engine.pack;

import java.util.List;

public final class PackValidationException extends RuntimeException {
    private final List<PackIssue> issues;

    public PackValidationException(List<PackIssue> issues) {
        super("Pack validation failed (" + issues.size() + " issues)");
        this.issues = List.copyOf(issues);
    }

    public List<PackIssue> issues() { return issues; }
}