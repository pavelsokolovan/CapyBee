package com.capybee.engine.session.wire;

import java.util.Map;

public record ErrorDto(Error error) {
    public record Error(String code, String message, Map<String, Object> details) {
        public Error {
            details = Map.copyOf(details);
        }
    }
}
