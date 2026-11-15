package com.capybee.engine.api;

import com.capybee.engine.api.json.SnapshotCodec;
import com.capybee.engine.adapter.Randoms;
import com.capybee.engine.adapter.UuidIdSource;
import com.capybee.engine.pack.*;
import com.capybee.engine.pack.json.PackLoader;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

/** Game-specific identifiers/assertions are data; the SDK and test harness stay neutral. */
class AuthoredReferenceRoutesTest {
    final ObjectMapper json=new ObjectMapper();
    JsonNode specifications() throws Exception {
        return json.readTree(Files.readString(Path.of("../fixtures/reference-authored-routes.json"))).path("packs");
    }
    Pack load(JsonNode specification) {
        var id=new PackId(specification.path("packId").asString());
        var loaded=new PackLoader(Engine.sectionValidators()).tryLoad(id,new DirectoryPackSource(Path.of("../reference-game-packs",id.value())));
        assertThat(loaded.ok()).as("reference Pack validates").isTrue();
        return loaded.pack().orElseThrow();
    }
    Engine engine(Pack pack,AtomicInteger calls) {
        return Engine.builder(new PackRegistry(List.of(pack))).clock(Clock.systemUTC()).ids(new UuidIdSource())
                .random(Randoms.seeded(7)).textGenerator(request->{calls.incrementAndGet();throw new AssertionError("PROVIDER_MUST_NOT_BE_USED");}).build();
    }
    @Test void routesResumeEveryAcceptedRevisionWithoutProviderAndRespectUnavailableMoves() throws Exception {
        for(var spec:specifications()) {
            var pack=load(spec);var calls=new AtomicInteger();var engine=engine(pack,calls);
            assertThat(pack.manifest().version().toString()).isEqualTo(spec.path("version").asString());
            assertThat(pack.manifest().features()).doesNotContain(PackFeature.GENERATION);
            assertThat(pack.manifest().contentPolicy().freeTextAllowed()).isFalse();
            assertThat(fingerprint(pack)).isEqualTo(spec.path("resourceFingerprint").asString());
            for(var route:spec.path("routes")) {
                var start=engine.start(new RunRequest(pack.manifest().id(),new LocaleTag(spec.path("locale").asString()),Map.of(),7L));
                assertThat(start.ok()).isTrue();Run run=start.run();
                var before=run.snapshot();assertThat(run.apply(new Command.Move(spec.path("rejectedMove").asString())).accepted()).isFalse();
                assertThat(run.snapshot()).isEqualTo(before);
                assertThat(run.view().places().places()).hasSize(spec.path("places").asInt());
                for(var command:route.path("commands")) {
                    Command action=switch(command.path("type").asString()) {
                        case "ADVANCE"->new Command.Advance();case "MOVE"->new Command.Move(command.path("id").asString());
                        default->new Command.Choose(command.path("id").asString());
                    };
                    var accepted=run.apply(action);assertThat(accepted.accepted()).as("route %s command %s",route.path("id").asString(),command).isTrue();
                    var snapshot=SnapshotCodec.fromJson(SnapshotCodec.toJson(run.snapshot()));
                    var resumed=engine.resume(snapshot.snapshot());assertThat(resumed.ok()).isTrue();
                    assertThat(resumed.run().snapshot()).isEqualTo(run.snapshot());assertThat(resumed.run().view()).isEqualTo(run.view());run=resumed.run();
                }
                assertThat(run.view().endReason()).isEqualTo(EndReason.COMPLETED);
                assertThat(run.view().outcome()).isEqualTo(route.path("outcome").asString());
                assertThat(run.view().blocks().stream().map(b->b.text()).toList()).anyMatch(t->t.contains(route.path("requiredText").asString()));
                for(var entry:route.path("expectedFlags").properties())assertThat(run.snapshot().state().flags()).containsEntry(entry.getKey(),entry.getValue().asBoolean());
            }
            assertThat(calls).hasValue(0);
            var abandoned=engine.start(new RunRequest(pack.manifest().id(),null,Map.of(),7L)).run();
            abandoned.apply(new Command.Abandon());assertThat(abandoned.view().endReason()).isEqualTo(EndReason.ABANDONED);
            assertThat(abandoned.view().outcome()).isNull();
        }
    }
    @Test void allReachableMechanicalStatesCanReachAnAuthoredResolution() throws Exception {
        for(var spec:specifications()) {
            var pack=load(spec);var calls=new AtomicInteger();var engine=engine(pack,calls);
            // This suite's finite-state key is valid only for flag-only authored rules.
            var rules=json.readTree(pack.requireFile("rules.json"));assertThat(rules.path("counters").isEmpty()).isTrue();assertThat(rules.path("sets").isEmpty()).isTrue();
            assertThat(pack.requireFile("rules.json")).doesNotContain("VISITED_","STEP_","COUNTER_","SET_CONTAINS");
            assertThat(pack.requireFile("scenes/"+spec.path("locale").asString()+"/story.json")).doesNotContain("VISITED_","STEP_","COUNTER_","SET_CONTAINS");
            var first=engine.start(new RunRequest(pack.manifest().id(),null,Map.of(),7L)).run().snapshot();
            var states=new LinkedHashMap<String,RunSnapshot>();var pending=new ArrayDeque<RunSnapshot>();states.put(key(first),first);pending.add(first);
            var reverse=new HashMap<String,Set<String>>();var endings=new HashSet<String>();var outcomes=new HashSet<String>();
            while(!pending.isEmpty()) {
                var snapshot=pending.remove();var source=key(snapshot);Run run=engine.resume(snapshot).run();
                if(run.view().ended()){endings.add(source);outcomes.add(run.view().outcome());continue;}
                var commands=new ArrayList<Command>();
                run.view().options().stream().filter(Option::enabled).forEach(o->commands.add(new Command.Choose(o.id())));
                if(run.view().canAdvance())commands.add(new Command.Advance());
                run.view().places().availableMoves().forEach(id->commands.add(new Command.Move(id)));
                assertThat(commands).as("reachable state has a legal action").isNotEmpty();
                for(var command:commands) {
                    var branch=engine.resume(snapshot).run();assertThat(branch.apply(command).accepted()).isTrue();var next=branch.snapshot();var target=key(next);
                    reverse.computeIfAbsent(target,k->new HashSet<>()).add(source);
                    if(!states.containsKey(target)){states.put(target,next);pending.add(next);}
                }
                assertThat(states.size()).isLessThan(10000);
            }
            var canFinish=new HashSet<>(endings);var back=new ArrayDeque<>(endings);
            while(!back.isEmpty())for(var previous:reverse.getOrDefault(back.remove(),Set.of()))if(canFinish.add(previous))back.add(previous);
            assertThat(canFinish).containsExactlyInAnyOrderElementsOf(states.keySet());
            System.out.println("AUTHORED_REFERENCE_PLAYABILITY states="+states.size()+" endings="+outcomes.size());
            assertThat(outcomes).containsExactlyInAnyOrderElementsOf(json.convertValue(spec.path("outcomes"),List.class));assertThat(calls).hasValue(0);
        }
    }
    String key(RunSnapshot snapshot) {var s=snapshot.state();return s.sceneId()+"|"+new TreeMap<>(s.flags())+"|"+s.currentPlaceId();}
    String fingerprint(Pack pack) throws Exception {
        var root=Path.of("../reference-game-packs",pack.manifest().id().value());var files=new ArrayList<>(pack.manifest().files());files.add("pack.json");Collections.sort(files);
        var digest=MessageDigest.getInstance("SHA-256");for(var file:files){digest.update(file.getBytes(StandardCharsets.UTF_8));digest.update((byte)0);digest.update(Files.readAllBytes(root.resolve(file)));}
        return HexFormat.of().formatHex(digest.digest());
    }
}
