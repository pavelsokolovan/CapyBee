package com.capybee.engine.session;

import com.capybee.engine.adapter.Randoms;
import com.capybee.engine.adapter.SequentialIdSource;
import com.capybee.engine.api.DynamicGameBootstrapRequest;
import com.capybee.engine.api.DynamicGameBootstrapResult;
import com.capybee.engine.api.Command;
import com.capybee.engine.api.Engine;
import com.capybee.engine.api.RunSnapshot;
import com.capybee.engine.generation.blueprint.BlueprintCancellation;
import com.capybee.engine.generation.blueprint.BlueprintFallbackResolver;
import com.capybee.engine.generation.blueprint.BlueprintGenerationRequest;
import com.capybee.engine.generation.blueprint.BlueprintGenerationService;
import com.capybee.engine.generation.blueprint.DefaultBlueprintOutputReviewer;
import com.capybee.engine.generation.blueprint.DefaultBlueprintSemanticValidator;
import com.capybee.engine.generation.profile.HostGenerationCeilings;
import com.capybee.engine.generation.profile.PreparationPresetResolver;
import com.capybee.engine.generation.profile.ValidatedGeneratorProfile;
import com.capybee.engine.generation.profile.json.GeneratorProfileLoader;
import com.capybee.engine.input.DefaultInputGuard;
import com.capybee.engine.pack.PackRegistry;
import com.capybee.engine.port.StructuredGenerationRequest;
import com.capybee.engine.port.TextGenerationResult;
import com.capybee.engine.port.TextGenerator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Produces a real, verified dynamic snapshot for session adapter contract tests. */
public final class DynamicSnapshotFixture {
    private DynamicSnapshotFixture() {}

    public static RunSnapshot create(UUID runId, Clock clock) {
        try {
            ValidatedGeneratorProfile profile = profile();
            var resolved = new PreparationPresetResolver().resolve(profile, Map.of(),
                    profile.profile().defaultLocale(), HostGenerationCeilings.none());
            String blueprint = Files.readString(Path.of("..", "certification", "dynamic-v1", "blueprint.json"),
                    StandardCharsets.UTF_8);
            TextGenerator provider = request -> new TextGenerationResult(
                    request.purpose() == StructuredGenerationRequest.Purpose.GAME_BLUEPRINT ? blueprint : "{}",
                    new TextGenerationResult.TokenUsage(1, 1, 0, 0), "session-test-fixture",
                    TextGenerationResult.FinishReason.COMPLETE);
            var ids = new SequentialIdSource("dynamic-test");
            var generation = new BlueprintGenerationService(provider, new DefaultBlueprintOutputReviewer(),
                    new DefaultBlueprintSemanticValidator(), ids, BlueprintFallbackResolver.NONE, clock);
            var request = new BlueprintGenerationRequest(runId.toString(), 1, 1, profile,
                    resolved.preferences().orElseThrow(), resolved.constraints().orElseThrow(), null,
                    new DefaultInputGuard(), BlueprintCancellation.NEVER,
                    clock.instant().plus(Duration.ofMinutes(1)));
            Engine engine = Engine.builder(new PackRegistry(List.of())).clock(clock).random(Randoms.seeded(7L))
                    .ids(ids).textGenerator(provider).inputGuard(new DefaultInputGuard())
                    .dynamicBlueprints(generation).build();
            var result = engine.startDynamic(new DynamicGameBootstrapRequest(request, 7L));
            RunSnapshot generated = ((DynamicGameBootstrapResult.Started) result).run().snapshot();
            return new RunSnapshot(generated.snapshotVersion(), runId.toString(), generated.pack(), generated.dynamic(),
                    generated.locale(), generated.seed(), generated.startInputs(), generated.createdAt(),
                    generated.updatedAt(), generated.step(), generated.state());
        } catch (Exception failure) {
            throw new AssertionError("Could not create the dynamic session fixture", failure);
        }
    }

    public static RunSnapshot ended(UUID runId, Clock clock) {
        RunSnapshot initial = create(runId, clock);
        Engine engine = Engine.builder(new PackRegistry(List.of())).clock(clock)
                .random(Randoms.seeded(initial.seed()))
                .ids(new SequentialIdSource("dynamic-resume-test")).build();
        var resumed = engine.resume(initial);
        if (!resumed.ok()) throw new AssertionError("Could not resume the dynamic session fixture: " + resumed.issues());
        var run = resumed.run();
        for (int step = 0; !run.view().ended() && step < 16; step++) {
            var choice = run.view().options().stream().filter(option -> option.enabled()).findFirst().orElseThrow();
            run.apply(new Command.Choose(choice.id()));
        }
        if (!run.view().ended()) throw new AssertionError("Dynamic session fixture did not end");
        return run.snapshot();
    }

    private static ValidatedGeneratorProfile profile() throws Exception {
        Path root = Path.of("..", "generator-profiles", "generic-adventure").toAbsolutePath().normalize();
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                files.put(root.relativize(path).toString().replace('\\', '/'), Files.readAllBytes(path));
            }
        }
        return new GeneratorProfileLoader().load(files).profile().orElseThrow();
    }
}
