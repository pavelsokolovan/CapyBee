package com.capybee.server.web.dto;

import java.util.Map;
import java.util.UUID;

public record CreateGameResultRequest(
        UUID id,
        String gameKey,
        Integer durationMs,
        Map<String, Object> metrics) {
}
