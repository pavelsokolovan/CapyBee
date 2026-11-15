package com.capybee.engine.api;

import com.capybee.engine.adapter.*;
import com.capybee.engine.generation.GeneratedGameProfile;
import com.capybee.engine.pack.*;
import com.capybee.engine.pack.json.PackLoader;
import com.capybee.engine.api.json.SnapshotCodec;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import java.time.Clock;
import static org.assertj.core.api.Assertions.*;

/** Concrete content identifiers come exclusively from the reference inventory. */
class VersionedPresentationTest {
    final ObjectMapper json=new ObjectMapper();
    @Test void invalidProviderOutputUsesStrictReviewedFallbackWithinAttemptBudget() throws Exception {
        var spec=json.readTree(Files.readString(Path.of("../fixtures/reference-authored-routes.json"))).path("packs").get(0);
        var id=new PackId(spec.path("packId").asString());
        var pack=new PackLoader(Engine.sectionValidators()).load(id,new DirectoryPackSource(Path.of("../reference-game-pack-versions",id.value(),"1.1.0")));
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        var engine=Engine.builder(new PackRegistry(List.of(pack))).clock(Clock.systemUTC()).ids(new UuidIdSource()).random(Randoms.seeded(7)).textGenerator(request->{
            calls.incrementAndGet();return new com.capybee.engine.port.TextGenerationResult("{\"invented-mechanic\":\"https://example.invalid\"}",null,"invalid-double",com.capybee.engine.port.TextGenerationResult.FinishReason.COMPLETE);
        }).build();
        var start=engine.start(new RunRequest(id,null,Map.of(),7L,true));assertThat(start.ok()).isTrue();
        assertThat(calls).hasValue(10);assertThat(start.run().generationSummary().orElseThrow().totalProviderCalls()).isEqualTo(10);
        assertThat(start.run().snapshot().state().generationArtifacts()).hasSize(5).allMatch(a->a.outcome()==com.capybee.engine.generation.GenerationArtifact.Outcome.FALLBACK);
        assertThat(SnapshotCodec.toJson(start.run().snapshot())).doesNotContain("invented-mechanic","https://example.invalid");
        assertThat(engine.resume(start.run().snapshot()).ok()).isTrue();assertThat(calls).hasValue(10);
    }
    @Test void reviewedPresentationCoversEveryRouteAndRetainsPreviousVersion() throws Exception {
        var spec=json.readTree(Files.readString(Path.of("../fixtures/reference-authored-routes.json"))).path("packs").get(0);
        var id=new PackId(spec.path("packId").asString());
        var loader=new PackLoader(Engine.sectionValidators());
        var old=loader.load(id,new DirectoryPackSource(Path.of("../reference-game-packs",id.value())));
        var loaded=loader.tryLoad(id,new DirectoryPackSource(Path.of("../reference-game-pack-versions",id.value(),"1.1.0")));
        assertThat(loaded.issues()).isEmpty();var current=loaded.pack().orElseThrow();
        assertThat(current.generatedGameProfile().orElseThrow().kind()).isEqualTo(GeneratedGameProfile.Kind.AUTHORED_GRAPH);
        for(var file:old.files().entrySet()) if(!file.getKey().equals("pack.json")&&!file.getKey().startsWith("strings/"))assertThat(current.requireFile(file.getKey())).isEqualTo(file.getValue());
        var registry=new PackRegistry(List.of(old,current));assertThat(registry.all()).containsExactly(current);
        var offline=Engine.builder(registry).clock(Clock.systemUTC()).ids(new UuidIdSource()).random(Randoms.seeded(7)).build();
        var legacy=Engine.builder(new PackRegistry(List.of(old))).clock(Clock.systemUTC()).ids(new UuidIdSource()).random(Randoms.seeded(7)).build()
            .start(new RunRequest(id,null,Map.of(),7L)).run();
        assertThat(offline.resume(legacy.snapshot()).run().view()).isEqualTo(legacy.view());
        for(boolean generated:List.of(false,true)) for(var route:spec.path("routes")) {
            var start=offline.start(new RunRequest(id,null,Map.of("story-tone","cozy"),7L,generated));
            assertThat(start.issues()).noneMatch(i->i.severity()==Issue.Severity.ERROR);assertThat(start.ok()).isTrue();
            var run=start.run();if(generated)assertThat(run.view().story()).isNotNull();
            for(var cmd:route.path("commands")) {
                var action=switch(cmd.path("type").asString()) {case "ADVANCE"->new Command.Advance();case "MOVE"->new Command.Move(cmd.path("id").asString());default->new Command.Choose(cmd.path("id").asString());};
                assertThat(run.apply(action).accepted()).as("%s",cmd).isTrue();
                var resume=offline.resume(SnapshotCodec.fromJson(SnapshotCodec.toJson(run.snapshot())).snapshot());
                assertThat(resume.issues()).isEmpty();assertThat(resume.run().view()).isEqualTo(run.view());run=resume.run();
            }
            assertThat(run.view().endReason()).isEqualTo(EndReason.COMPLETED);assertThat(run.view().outcome()).isEqualTo(route.path("outcome").asString());
        }
    }
}
