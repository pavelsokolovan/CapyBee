package com.capybee.engine.generation.profile;

import java.util.List;
import java.util.Optional;

public record PreparationResolution(Optional<ResolvedGamePreferences> preferences,
        Optional<EffectiveGenerationConstraints> constraints, List<ProfileIssue> issues) {
    public PreparationResolution { issues = List.copyOf(issues); }
    public boolean valid() { return preferences.isPresent() && constraints.isPresent() && issues.isEmpty(); }
}
