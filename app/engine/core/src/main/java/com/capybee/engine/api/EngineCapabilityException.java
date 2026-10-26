package com.capybee.engine.api;

public final class EngineCapabilityException extends IllegalStateException {
    private final String code;

    public EngineCapabilityException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() { return code; }
}
