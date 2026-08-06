package com.capybee.server.web.dto;

import java.util.UUID;

public record CreateCheckInRequest(
        UUID id,
        String mood,
        String note) {
}
