package com.capybee.engine.api;

import com.capybee.engine.adapter.SequentialIdSource;
import com.capybee.engine.adapter.Randoms;
import com.capybee.engine.pack.DirectoryPackSource;
import com.capybee.engine.pack.PackId;
import com.capybee.engine.pack.json.PackLoader;
import com.capybee.engine.pack.PackRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;

class EngineRuntimeTest {
    private Engine minimalEngine() {
        var source = new DirectoryPackSource(Path.of("..", "fixtures", "game-packs", "fixture-minimal"));
        var pack = new PackLoader(Engine.sectionValidators()).load(new PackId("fixture-minimal"), source);
        return Engine.builder(new PackRegistry(java.util.List.of(pack)))
                .clock(Clock.fixed(java.time.Instant.parse("2026-10-08T10:00:00Z"), java.time.ZoneOffset.UTC))
                .random(Randoms.seeded(42L))
                .ids(new SequentialIdSource("run"))
                .build();
    }

    @Test
    void startAndAdvanceMinimalFixture() {
        var engine = minimalEngine();

        var request = new RunRequest(new PackId("fixture-minimal"), null, Map.of(), 99L);
        var result = engine.start(request);
        assertThat(result.ok()).isTrue();
        assertThat(result.run()).isNotNull();
        assertThat(result.run().view().options()).hasSize(2);

        var choice = result.run().apply(new Command.Choose("boat"));
        assertThat(choice.accepted()).isTrue();
        assertThat(choice.view().blocks()).isNotEmpty();

        var next = result.run().apply(new Command.Advance());
        assertThat(next.accepted()).isTrue();
        assertThat(next.view().blocks()).isNotEmpty();
    }

    @Test
    void invalidStartRequestFails() {
        var engine = minimalEngine();

        var result = engine.start(new RunRequest(new PackId("missing"), null, Map.of(), 99L));

        assertThat(result.ok()).isFalse();
        assertThat(result.issues()).anyMatch(issue -> issue.code().equals("PACK_NOT_FOUND"));
    }

    @Test
    void invalidCommandsAreRejectedAndAbandoningEndsRun() {
        var engine = minimalEngine();

        var started = engine.start(new RunRequest(new PackId("fixture-minimal"), null, Map.of(), 99L));
        var rejectedAdvance = started.run().apply(new Command.Advance());
        assertThat(rejectedAdvance.accepted()).isFalse();
        assertThat(rejectedAdvance.issues()).anyMatch(issue -> issue.code().equals("COMMAND_NOT_ALLOWED"));

        var invalidChoice = started.run().apply(new Command.Choose("missing"));
        assertThat(invalidChoice.accepted()).isFalse();
        assertThat(invalidChoice.issues()).anyMatch(issue -> issue.code().equals("OPTION_UNKNOWN"));

        var abandoned = started.run().apply(new Command.Abandon());
        assertThat(abandoned.accepted()).isTrue();
        assertThat(abandoned.view().ended()).isTrue();
        assertThat(abandoned.view().endReason()).isEqualTo(EndReason.ABANDONED);
    }

    @Test
    void placeMoveIsRejectedWhenFeatureIsNotEnabled() {
        var engine = minimalEngine();
        var started = engine.start(new RunRequest(new PackId("fixture-minimal"), null, Map.of(), 99L));

        var move = started.run().apply(new Command.Move("somewhere"));

        assertThat(started.run().view().places()).isNull();
        assertThat(move.accepted()).isFalse();
        assertThat(move.issues()).extracting(Issue::code).containsExactly("PLACES_NOT_ENABLED");
    }

    @Test
    void snapshotCanBeResumed() {
        var engine = minimalEngine();

        var started = engine.start(new RunRequest(new PackId("fixture-minimal"), null, Map.of(), 99L));
        started.run().apply(new Command.Choose("boat"));
        var snapshot = started.run().snapshot();

        var resumed = engine.resume(snapshot);

        assertThat(resumed.ok()).isTrue();
        assertThat(resumed.run()).isNotNull();
        assertThat(resumed.run().view().blocks()).isNotEmpty();
    }
}
