package com.capybee.engine.pack;

import java.util.List;
import java.util.Optional;

public record LoadResult(Optional<Pack> pack, List<PackIssue> issues) {
    public LoadResult { issues = List.copyOf(issues); }

    public boolean ok() {
        return pack.isPresent() && issues.stream().noneMatch(issue -> issue.severity() == PackIssue.Severity.ERROR);
    }
}