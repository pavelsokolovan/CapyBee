package com.capybee.server.web.dto;

import java.util.UUID;

public record MissionResponse(
        UUID id,
        String code,
        String title,
        String timeHint,
        String description,
        boolean active) {
}
