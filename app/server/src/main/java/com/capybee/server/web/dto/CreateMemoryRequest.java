package com.capybee.server.web.dto;

import java.util.UUID;

public record CreateMemoryRequest(
        UUID id,
        String worldType,
        String title,
        String textContent,
        String mediaUrl,
        Boolean isFavorite) {
}
