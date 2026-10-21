package com.capybee.engine.api;

public record Issue(Severity severity, String code, String message) {
    public enum Severity { ERROR, WARNING }
}
