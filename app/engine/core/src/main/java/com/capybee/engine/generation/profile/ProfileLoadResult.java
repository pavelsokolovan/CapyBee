package com.capybee.engine.generation.profile;

import java.util.List;
import java.util.Optional;

public record ProfileLoadResult(Optional<ValidatedGeneratorProfile> profile, List<ProfileIssue> issues) {
    public ProfileLoadResult { profile = profile == null ? Optional.empty() : profile; issues = List.copyOf(issues); }
    public boolean valid() { return profile.isPresent() && issues.isEmpty(); }
}
