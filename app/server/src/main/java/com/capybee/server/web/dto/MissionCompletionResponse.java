package com.capybee.server.web.dto;

import java.time.Instant;
import java.util.UUID;

public record MissionCompletionResponse(
        UUID id,
        UUID missionId,
        String missionCode,
        String title,
        UUID profileId,
        Instant completedAt,
        String note) {
}
