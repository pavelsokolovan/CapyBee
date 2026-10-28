package com.capybee.engine.cli;

import com.capybee.engine.port.StructuredGenerationRequest;
import com.capybee.engine.port.TextGenerationResult;
import com.capybee.engine.port.TextGenerator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Deterministic, file-bounded provider used only by explicitly enabled offline certification. */
final class CertificationFixtureTextGenerator implements TextGenerator {
    private final String blueprint;

    private CertificationFixtureTextGenerator(String blueprint) { this.blueprint = blueprint; }

    static CertificationFixtureTextGenerator open(Path directory) {
        try {
            Path root = directory.toAbsolutePath().normalize();
            Path file = root.resolve("blueprint.json").normalize();
            if (!file.startsWith(root) || Files.isSymbolicLink(file)
                    || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > 1_048_576)
                throw new IllegalArgumentException("CERTIFICATION_FIXTURE_INVALID");
            return new CertificationFixtureTextGenerator(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException failure) { throw new IllegalArgumentException("CERTIFICATION_FIXTURE_INVALID", failure); }
    }

    @Override public TextGenerationResult generate(StructuredGenerationRequest request) {
        String output = request.purpose() == StructuredGenerationRequest.Purpose.GAME_BLUEPRINT ? blueprint : "{}";
        return new TextGenerationResult(output, new TextGenerationResult.TokenUsage(1, 1, 0, 0),
                "offline-certification-fixture", TextGenerationResult.FinishReason.COMPLETE);
    }
}
