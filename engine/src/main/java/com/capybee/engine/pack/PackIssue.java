package com.capybee.engine.pack;

public record PackIssue(Severity severity, String code, String file, String pointer, String message) {
    public enum Severity { ERROR, WARNING }
}