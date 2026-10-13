package com.capybee.engine.port;

@FunctionalInterface
public interface TextGenerator {
    TextGenerationResult generate(StructuredGenerationRequest request);
}