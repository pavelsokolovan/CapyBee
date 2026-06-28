package com.capybee.server.web.dto;

public record CreateFriendshipRequest(
        String personLabel,
        String stage,
        String note) {
}
