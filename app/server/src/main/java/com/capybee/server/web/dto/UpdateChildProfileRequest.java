package com.capybee.server.web.dto;

public record UpdateChildProfileRequest(
        String nickname,
        Integer birthYear,
        String preferredLocale,
        String avatarSeed,
        Boolean active,
        Boolean hasSeenOnboarding) {
}
