package com.capybee.engine.api;

import com.capybee.engine.generation.GenerationExecutionSummary;

import java.util.Optional;

public interface Run {
    String id();

    View view();

    StepResult apply(Command command);

    RunSnapshot snapshot();

    default Optional<GenerationExecutionSummary> generationSummary() { return Optional.empty(); }
}
