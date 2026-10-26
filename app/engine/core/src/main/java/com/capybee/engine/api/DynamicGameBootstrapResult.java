package com.capybee.engine.api;

import com.capybee.engine.generation.blueprint.BlueprintGenerationResult;
import com.capybee.engine.generation.blueprint.MaterializedGameDefinition;

import java.util.List;
import java.util.Objects;

public sealed interface DynamicGameBootstrapResult permits DynamicGameBootstrapResult.Started,
        DynamicGameBootstrapResult.FallbackOffered, DynamicGameBootstrapResult.Failed {
    record Started(Run run, MaterializedGameDefinition definition,
                   List<Issue> issues, List<Event> events) implements DynamicGameBootstrapResult {
        public Started {
            Objects.requireNonNull(run); Objects.requireNonNull(definition);
            issues = List.copyOf(issues); events = List.copyOf(events);
        }
        @Override public String toString() { return "Started[run=" + run.id() + ", definition=redacted]"; }
    }
    record FallbackOffered(BlueprintGenerationResult.FallbackOffer offer,
                           List<Issue> issues) implements DynamicGameBootstrapResult {
        public FallbackOffered { Objects.requireNonNull(offer); issues = List.copyOf(issues); }
    }
    record Failed(String code, List<Issue> issues) implements DynamicGameBootstrapResult {
        public Failed { Objects.requireNonNull(code); issues = List.copyOf(issues); }
    }
}
