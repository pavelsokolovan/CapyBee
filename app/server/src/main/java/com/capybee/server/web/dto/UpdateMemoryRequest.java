package com.capybee.server.web.dto;

public record UpdateMemoryRequest(
        String worldType,
        String title,
        String textContent,
        String mediaUrl,
        Boolean isFavorite) {
}
