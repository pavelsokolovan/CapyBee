package com.capybee.engine.generation.blueprint;

import java.util.List;
import java.util.Optional;

public record BlueprintParseResult(Optional<ParsedGameBlueprint> blueprint, List<BlueprintIssue> issues) {
    public BlueprintParseResult { blueprint = blueprint == null ? Optional.empty() : blueprint; issues = List.copyOf(issues); }
    public static BlueprintParseResult accepted(ParsedGameBlueprint value) { return new BlueprintParseResult(Optional.of(value), List.of()); }
    public static BlueprintParseResult rejected(BlueprintIssue issue) { return new BlueprintParseResult(Optional.empty(), List.of(issue)); }
    public boolean accepted() { return blueprint.isPresent(); }
}
