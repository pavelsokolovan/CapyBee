package com.capybee.server.web.dto;

import java.util.UUID;

public record CreateFriendshipRequest(
        UUID id,
        String personLabel,
        String stage,
        String note) {
}
