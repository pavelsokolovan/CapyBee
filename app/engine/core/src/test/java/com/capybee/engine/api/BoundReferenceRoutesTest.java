package com.capybee.engine.api;

import com.capybee.engine.adapter.*;
import com.capybee.engine.api.json.SnapshotCodec;
import com.capybee.engine.pack.*;
import com.capybee.engine.pack.json.PackLoader;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

/** Reference-specific identifiers are supplied by the reviewed route inventory. */
class BoundReferenceRoutesTest {
    final ObjectMapper json=new ObjectMapper();
    @Test void allBoundRoutesResumeWithFallbackAndRetainedVersionsKeepLocationOnlyMovement() throws Exception {
        var spec=json.readTree(Files.readString(Path.of("../fixtures/reference-board-routes.json")));
        var id=new PackId(spec.path("packId").asString());var loader=new PackLoader(Engine.sectionValidators());
        var loaded=loader.tryLoad(id,new DirectoryPackSource(Path.of("../reference-game-pack-versions",id.value(),spec.path("version").asString())));
        assertThat(loaded.issues()).isEmpty();var pack=loaded.pack().orElseThrow();
        for(boolean generated:List.of(false,true))for(var route:spec.path("routes")) {
            var calls=new AtomicInteger();
            var engine=Engine.builder(new PackRegistry(List.of(pack))).clock(Clock.systemUTC()).ids(new UuidIdSource()).random(Randoms.seeded(7))
                .textGenerator(request->{calls.incrementAndGet();throw new IllegalStateException("FIXTURE_UNAVAILABLE");}).build();
            var started=engine.start(new RunRequest(id,null,Map.of("story-tone","cozy"),7L,generated));assertThat(started.ok()).isTrue();var run=started.run();
            var before=run.snapshot();int budget=calls.get();
            String adjacent=run.view().places().links().getFirst().to();
            assertThat(run.apply(new Command.Move(adjacent)).accepted()).isFalse(); // arrival navigation restriction
            assertThat(run.snapshot()).isEqualTo(before);assertThat(calls).hasValue(budget);
            for(var command:route.path("commands")) {
                Command action=switch(command.path("type").asString()) {case "ADVANCE"->new Command.Advance();case "MOVE"->new Command.Move(command.path("id").asString());default->new Command.Choose(command.path("id").asString());};
                var step=run.snapshot().step();var result=run.apply(action);assertThat(result.accepted()).as("%s %s",route.path("id"),command).isTrue();
                assertThat(run.snapshot().step()).isEqualTo(step+1);
                if(action instanceof Command.Move move)assertThat(result.view().places().currentPlaceId()).isEqualTo(move.placeId());
                var snapshot=SnapshotCodec.fromJson(SnapshotCodec.toJson(run.snapshot())).snapshot();
                // A fresh provider-disabled engine must need no additional generation to restore.
                var offline=Engine.builder(new PackRegistry(List.of(pack))).clock(Clock.systemUTC()).ids(new UuidIdSource()).random(Randoms.seeded(7)).build();
                var resumed=offline.resume(snapshot);assertThat(resumed.ok()).isTrue();assertThat(resumed.run().view()).isEqualTo(run.view());
            }
            assertThat(run.view().outcome()).isEqualTo(route.path("outcome").asString());assertThat(run.view().ended()).isTrue();
        }
        for(String version:List.of("1.1.0","1.2.0")) {
            var old=loader.load(id,new DirectoryPackSource(Path.of("../reference-game-pack-versions",id.value(),version)));
            var e=Engine.builder(new PackRegistry(List.of(old,pack))).clock(Clock.systemUTC()).ids(new UuidIdSource()).random(Randoms.seeded(7)).build();
            var legacy=Engine.builder(new PackRegistry(List.of(old))).clock(Clock.systemUTC()).ids(new UuidIdSource()).random(Randoms.seeded(7)).build().start(new RunRequest(id,null,Map.of(),7L)).run();
            var initial=legacy.view();var next=initial.places().availableMoves().getFirst();
            assertThat(legacy.apply(new Command.Move(next)).accepted()).isTrue();assertThat(legacy.view().blocks()).isEqualTo(initial.blocks());
            assertThat(e.resume(legacy.snapshot()).run().view()).isEqualTo(legacy.view());
        }
    }
}
