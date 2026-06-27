package com.capybee.server.web.dto;

public record CreateCheckInRequest(
        String mood,
        String note) {
}
