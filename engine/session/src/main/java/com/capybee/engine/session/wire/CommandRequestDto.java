package com.capybee.engine.session.wire;

import java.util.UUID;

public record CommandRequestDto(UUID id, Long expectedRevision, CommandDto command) {
    public record CommandDto(String type, String optionId, String text, String placeId) {}
}
