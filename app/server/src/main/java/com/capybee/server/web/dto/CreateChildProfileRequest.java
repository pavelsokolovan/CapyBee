package com.capybee.server.web.dto;

public record CreateChildProfileRequest(
        String nickname,
        Integer birthYear,
        String preferredLocale,
        String avatarSeed) {
}
