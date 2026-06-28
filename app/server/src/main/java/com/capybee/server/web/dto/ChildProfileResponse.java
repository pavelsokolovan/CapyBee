package com.capybee.server.web.dto;

import java.time.Instant;
import java.util.UUID;

public record ChildProfileResponse(
        UUID id,
        String nickname,
        Integer birthYear,
        String preferredLocale,
        String avatarSeed,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {
}
