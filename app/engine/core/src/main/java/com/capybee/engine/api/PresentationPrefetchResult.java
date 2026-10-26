package com.capybee.engine.api;

import com.capybee.engine.generation.GenerationArtifact;

/** Source-neutral, redacted prefetch result. */
public record PresentationPrefetchResult(String sceneId, GenerationArtifact.Outcome outcome,
                                         int providerCalls, String failureCode) {}
