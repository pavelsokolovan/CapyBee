package com.capybee.engine.session;

import java.util.Map;

import static com.capybee.engine.session.SessionTypes.ErrorCode;

public final class SessionException extends RuntimeException {
    private final ErrorCode code;
    private final Map<String, Object> details;

    public SessionException(ErrorCode code) {
        this(code, Map.of());
    }

    public SessionException(ErrorCode code, Map<String, Object> details) {
        super(code.name());
        this.code = code;
        this.details = Map.copyOf(details);
    }

    public ErrorCode code() { return code; }
    public Map<String, Object> details() { return details; }
}
