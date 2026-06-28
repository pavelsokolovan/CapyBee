package com.capybee.server.web.dto;

import java.time.Instant;
import java.util.UUID;

public record FriendshipResponse(
        UUID id,
        String personLabel,
        String stage,
        String note,
        Instant createdAt,
        Instant updatedAt) {
}
