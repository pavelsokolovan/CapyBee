package com.capybee.server.web.dto;

import java.time.Instant;
import java.util.UUID;

public record MissionInteractionResponse(
        UUID missionId,
        String action,
        Instant createdAt) {
}