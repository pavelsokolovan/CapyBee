package com.capybee.server.web.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record GameResultResponse(
        UUID id,
        String gameKey,
        Integer durationMs,
        Map<String, Object> metrics,
        Instant completedAt) {
}
