package com.capybee.server.web.dto;

import java.time.Instant;
import java.util.UUID;

public record MemoryResponse(
        UUID id,
        String worldType,
        String title,
        String textContent,
        String mediaUrl,
        boolean isFavorite,
        Instant createdAt,
        Instant updatedAt) {
}
