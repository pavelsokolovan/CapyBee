package com.capybee.server.web.dto;

import java.time.Instant;
import java.util.UUID;

public record CheckInResponse(
        UUID id,
        String mood,
        String note,
        Instant createdAt) {
}
