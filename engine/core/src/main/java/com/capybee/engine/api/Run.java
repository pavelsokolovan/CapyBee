package com.capybee.engine.api;

import com.capybee.engine.generation.GenerationExecutionSummary;
import com.capybee.engine.generation.GenerationArtifact;

import java.util.List;
import java.util.Optional;

public interface Run {
    String id();

    View view();

    StepResult apply(Command command);

    RunSnapshot snapshot();

    default Optional<GenerationExecutionSummary> generationSummary() { return Optional.empty(); }

    /** Generated scenes reachable in one step that have not been resolved yet. */
    default List<String> prefetchCandidates() { return List.of(); }

    /** Resolve a reachable generated scene without advancing the run. */
    default Optional<GenerationArtifact> prefetch(String sceneId) { return Optional.empty(); }
}
