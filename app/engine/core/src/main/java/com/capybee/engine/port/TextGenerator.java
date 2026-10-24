package com.capybee.engine.port;

@FunctionalInterface
public interface TextGenerator {
    TextGenerationResult generate(StructuredGenerationRequest request);

    default Capabilities capabilities() { return new Capabilities(true, 12_000, 64_000); }

    record Capabilities(boolean gameBlueprintStructuredOutput, int maxBlueprintOutputTokens,
                        int maxInputCharacters) {
        public Capabilities {
            if (maxBlueprintOutputTokens < 0 || maxInputCharacters < 1) {
                throw new IllegalArgumentException("Invalid text generator capabilities");
            }
        }
    }
}
