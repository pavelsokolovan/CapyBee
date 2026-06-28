package com.capybee.server.web.dto;

public record CreateMemoryRequest(
        String worldType,
        String title,
        String textContent,
        String mediaUrl,
        Boolean isFavorite) {
}
