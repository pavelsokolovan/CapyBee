package com.capybee.engine.generation.blueprint;

@FunctionalInterface
public interface BlueprintGenerator {
    BlueprintGenerationResult generate(BlueprintGenerationRequest request);
}
