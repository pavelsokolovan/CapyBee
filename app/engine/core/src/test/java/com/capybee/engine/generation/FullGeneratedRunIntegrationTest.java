package com.capybee.engine.generation;

import com.capybee.engine.adapter.Randoms;
import com.capybee.engine.adapter.SequentialIdSource;
import com.capybee.engine.api.Command;
import com.capybee.engine.api.EndReason;
import com.capybee.engine.api.Engine;
import com.capybee.engine.api.RunRequest;
import com.capybee.engine.pack.DirectoryPackSource;
import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.PackId;
import com.capybee.engine.pack.PackRegistry;
import com.capybee.engine.pack.json.PackLoader;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FullGeneratedRunIntegrationTest {
    private static final Path PACK = Path.of("..", "fixtures", "game-packs", "fixture-generated-full-minimal")
            .toAbsolutePath().normalize();

    @Test
    void providerFreeRunUsesValidatedFallbacksThroughGeneratedEnding() {
        var pack = new PackLoader(Engine.sectionValidators()).load(
                new PackId("fixture-generated-full-minimal"), new DirectoryPackSource(PACK));
        Engine engine = Engine.builder(new PackRegistry(List.of(pack))).clock(Clock.systemUTC())
                .random(Randoms.seeded(4)).ids(new SequentialIdSource("full-fallback")).build();

        var start = engine.start(new RunRequest(pack.manifest().id(), new LocaleTag("en"), Map.of(), 4L, true));
        assertThat(start.ok()).isTrue();
        var run = start.run();
        assertThat(run.view().blocks().getFirst().text()).startsWith("The Fork\n");

        assertThat(run.apply(new Command.Choose("left")).accepted()).isTrue();
        assertThat(run.apply(new Command.Advance()).accepted()).isTrue();
        assertThat(run.apply(new Command.Advance()).accepted()).isTrue();
        var ending = run.apply(new Command.Choose("succeed"));

        assertThat(ending.accepted()).isTrue();
        assertThat(ending.view().ended()).isTrue();
        assertThat(ending.view().endReason()).isEqualTo(EndReason.COMPLETED);
        assertThat(ending.view().outcome()).isEqualTo("success");
        assertThat(ending.view().blocks().getFirst().text()).startsWith("Home Again\n");
        assertThat(run.snapshot().state().generationArtifacts())
                .filteredOn(artifact -> artifact.stage() == GenerationCatalog.Stage.SCENE)
                .hasSize(5)
                .allMatch(artifact -> artifact.outcome() == GenerationArtifact.Outcome.FALLBACK);
    }
}
