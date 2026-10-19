package com.capybee.engine.session.wire;

import com.capybee.engine.api.Event;
import com.capybee.engine.api.Issue;
import com.capybee.engine.api.View;

import java.util.List;
import java.util.UUID;

public record CommandOutcomeDto(UUID commandId, String outcome, boolean accepted, long appliedRevision,
                                long revision, String status, View view, int viewSchema,
                                List<Event> events, List<Issue> issues) {
    public CommandOutcomeDto {
        events = List.copyOf(events);
        issues = List.copyOf(issues);
    }
}
