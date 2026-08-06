package com.capybee.server.web.dto;

import java.util.UUID;

public record CreateMissionCompletionRequest(
        UUID id,
        String note) {
}
