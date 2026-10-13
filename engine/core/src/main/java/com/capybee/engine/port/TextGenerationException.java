package com.capybee.engine.port;

public final class TextGenerationException extends RuntimeException {
    private final Code code;

    public TextGenerationException(Code code) {
        super(code.name());
        this.code = code;
    }

    public Code code() {
        return code;
    }

    @Override
    public String toString() {
        return "TextGenerationException[" + code + "]";
    }

    public enum Code {
        CONFIGURATION_ERROR,
        UNAVAILABLE,
        TIMEOUT,
        RATE_LIMITED,
        PROVIDER_REJECTED,
        INVALID_RESPONSE
    }
}