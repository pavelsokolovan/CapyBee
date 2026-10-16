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
        AUTHENTICATION_FAILED,
        PERMISSION_DENIED,
        REQUEST_INVALID,
        BILLING_UNAVAILABLE,
        STRUCTURED_OUTPUT_UNSUPPORTED,
        RESOURCE_NOT_FOUND,
        PROVIDER_REJECTED,
        INVALID_RESPONSE
    }
}