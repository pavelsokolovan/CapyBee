package com.capybee.engine.generation.blueprint;

import com.capybee.engine.adapter.Randoms;
import com.capybee.engine.adapter.SequentialIdSource;
import com.capybee.engine.api.*;
import com.capybee.engine.pack.PackRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DynamicBootstrapIntegrationTest {
    @Test void reviewedBlueprintStartsSharedRuntimeWithPendingPresentationAndNoFakeSnapshot() {
        var candidate = MaterializedGameCompilerTest.reviewed(MaterializedGameCompilerTest.fixture());
        Engine engine = Engine.builder(new PackRegistry(List.of()))
                .clock(Clock.fixed(Instant.parse("2026-10-26T10:00:00Z"), ZoneOffset.UTC))
                .random(Randoms.seeded(42L)).ids(new SequentialIdSource("run"))
                .dynamicBlueprints(request -> candidate).build();

        var result = engine.startDynamic(new DynamicGameBootstrapRequest(
                BlueprintGenerationServiceTest.context().request("dynamic-1", null, BlueprintCancellation.NEVER), 99L));

        assertThat(result).isInstanceOf(DynamicGameBootstrapResult.Started.class);
        var started = (DynamicGameBootstrapResult.Started) result;
        assertThat(started.issues()).extracting(Issue::code).contains("DYNAMIC_PRESENTATION_PENDING");
        assertThat(started.run().id()).isEqualTo("run-1");
        assertThat(started.run().view().options()).isEmpty();
        assertThat(started.run().snapshotCapability())
                .isEqualTo(SnapshotCapability.PENDING_DYNAMIC_DEFINITION_PERSISTENCE);
        assertThatThrownBy(started.run()::snapshot).isInstanceOfSatisfying(EngineCapabilityException.class,
                failure -> assertThat(failure.code()).isEqualTo("DYNAMIC_SNAPSHOT_UNAVAILABLE"));

        String firstChoice = started.definition().runtimeSceneSet().scenes()
                .get(started.definition().runtimeSceneSet().startId()).options().getFirst().id();
        assertThat(started.run().apply(new Command.Choose(firstChoice)).accepted()).isTrue();
    }

    @Test void missingCoordinatorFailsWithoutAllocatingIdentity() {
        var ids = new SequentialIdSource("run");
        Engine engine = Engine.builder(new PackRegistry(List.of()))
                .clock(Clock.systemUTC()).random(Randoms.seeded(1L)).ids(ids).build();
        var context = BlueprintGenerationServiceTest.context();

        var result = engine.startDynamic(new DynamicGameBootstrapRequest(
                context.request("dynamic-2", null, BlueprintCancellation.NEVER), null));

        assertThat(result).isEqualTo(new DynamicGameBootstrapResult.Failed("DYNAMIC_BLUEPRINT_FAILED",
                List.of(new Issue(Issue.Severity.ERROR, "DYNAMIC_BLUEPRINT_FAILED",
                        "Dynamic game startup could not complete at this stage"))));
        assertThat(ids.nextId()).isEqualTo("run-1");
    }
}
