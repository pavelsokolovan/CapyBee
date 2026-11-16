package com.capybee.engine.api;

import com.capybee.engine.adapter.*;
import com.capybee.engine.api.json.SnapshotCodec;
import com.capybee.engine.pack.*;
import com.capybee.engine.pack.json.PackLoader;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.time.Clock;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class PlaceEntryTest {
    final PackId id = new PackId("fixture-presentation");
    final DirectoryPackSource directory = new DirectoryPackSource(Path.of("../fixtures/game-packs", id.value()));
    final ObjectMapper json = new ObjectMapper();
    final PackLoader loader = new PackLoader(Engine.sectionValidators());
    ObjectNode tree(String path) {return (ObjectNode)json.readTree(directory.read(path).orElseThrow());}
    ObjectNode places() {
        var p = tree("places.json");p.put("schemaVersion",2);
        p.set("navigation",json.readTree("""
            {"allowedSceneIds":["camp","visitor"],"unavailableHintKey":"helper.need"}
            """));
        ((ObjectNode)p.path("places").get(0)).set("entry",json.readTree("{\"sceneId\":\"camp\"}"));
        ((ObjectNode)p.path("places").get(1)).set("entry",json.readTree("{\"sceneId\":\"visitor\"}"));
        return p;
    }
    PackSource source(Map<String,String> replacements) {
        return new PackSource() {
            public Optional<String> read(String path) {return Optional.ofNullable(replacements.get(path)).or(()->directory.read(path));}
            public String describe() {return "entry-fixture";}
        };
    }
    Engine engine(Pack pack) {return Engine.builder(new PackRegistry(List.of(pack))).clock(Clock.systemUTC()).ids(new UuidIdSource()).random(Randoms.seeded(2)).build();}
    Run start(Engine e) {return e.start(new RunRequest(id,null,Map.of(),2L)).run();}
    @Test void entersOneSceneInOneStepAndDiscoversOnlyAfterEntryWithExactResumeAndRevisits() {
        var pack=loader.load(id,source(Map.of("places.json",places().toString())));var e=engine(pack);var run=start(e);
        assertThat(run.view().characters()).isEmpty();assertThat(run.view().places().places().get(1).moveBehavior()).isEqualTo("ENTER_SCENE");
        var step=run.snapshot().step();var result=run.apply(new Command.Move("river"));assertThat(result.accepted()).isTrue();
        assertThat(run.snapshot().step()).isEqualTo(step+1);assertThat(run.snapshot().state().sceneId()).isEqualTo("visitor");
        assertThat(result.view().places().currentPlaceId()).isEqualTo("river");assertThat(result.view().characters()).hasSize(1);
        assertThat(result.view().presentation().party()).isEmpty();
        var before=run.snapshot();assertThat(run.apply(new Command.Move("river")).accepted()).isFalse();assertThat(run.snapshot()).isEqualTo(before);
        var restored=e.resume(SnapshotCodec.fromJson(SnapshotCodec.toJson(before)).snapshot()).run();assertThat(restored.view()).isEqualTo(run.view());
        assertThat(restored.apply(new Command.Move("camp")).accepted()).isTrue();assertThat(restored.apply(new Command.Move("river")).accepted()).isTrue();
        assertThat(restored.snapshot().state().visited()).containsExactly("camp","visitor");
        assertThat(restored.snapshot().state().visitedPlaces()).containsExactly("camp","river");
        assertThat(restored.apply(new Command.Choose("help")).accepted()).isTrue();
        assertThat(restored.view().places().availableMoves()).isEmpty();assertThat(restored.view().places().movementHint()).isEqualTo("Needs a quiet place.");
        before=restored.snapshot();assertThat(restored.apply(new Command.Move("camp")).accepted()).isFalse();assertThat(restored.snapshot()).isEqualTo(before);
    }
    @Test void conditionsUsePreMoveStateAndExposeOnlyLocalizedHints() {
        var p=places();var entry=(ObjectNode)p.path("places").get(1).path("entry");
        entry.set("requires",json.readTree("{\"type\":\"AT_PLACE\",\"place\":\"river\"}"));entry.put("unmetHintKey","helper.need");
        var run=start(engine(loader.load(id,source(Map.of("places.json",p.toString())))));
        assertThat(run.view().places().availableMoves()).isEmpty();assertThat(run.view().places().places().get(1).moveHint()).isEqualTo("Needs a quiet place.");
        var before=run.snapshot();assertThat(run.apply(new Command.Move("river")).accepted()).isFalse();assertThat(run.snapshot()).isEqualTo(before);
        assertThat(run.view().places().toString()).doesNotContain("AT_PLACE","sceneId","unmetHintKey");
    }
    @Test void sharedEntryEffectsObjectivesAndTerminalEntryExecuteOnceAndGoToDoesNotRecurse() {
        var p=places();var scenes=tree("scenes/en/story.json");
        ((ObjectNode)scenes.path("scenes").get(1)).set("onEnter",json.readTree("[{\"type\":\"FLAG_SET\",\"flag\":\"joined\",\"value\":true},{\"type\":\"GO_TO\",\"place\":\"camp\"}]"));
        var rules=tree("rules.json");rules.set("objectives",json.readTree("[{\"id\":\"visit\",\"labelKey\":\"helper.need\",\"completeWhen\":{\"type\":\"VISITED_SCENE\",\"scene\":\"visitor\"}}]"));
        var run=start(engine(loader.load(id,source(Map.of("places.json",p.toString(),"scenes/en/story.json",scenes.toString(),"rules.json",rules.toString())))));
        assertThat(run.apply(new Command.Move("river")).accepted()).isTrue();
        assertThat(run.snapshot().state().sceneId()).isEqualTo("visitor");assertThat(run.view().places().currentPlaceId()).isEqualTo("camp");
        assertThat(run.snapshot().state().flags()).containsEntry("joined",true);assertThat(run.snapshot().state().objectives()).containsEntry("visit",ObjectiveState.DONE);
        ((ObjectNode)p.path("places").get(1).path("entry")).put("sceneId","ending");
        run=start(engine(loader.load(id,source(Map.of("places.json",p.toString())))));
        assertThat(run.apply(new Command.Move("river")).accepted()).isTrue();assertThat(run.view().ended()).isTrue();assertThat(run.view().places().availableMoves()).isEmpty();
    }
    @Test void unexpectedCommitFailureRestoresMechanicsWithoutResettingPreparationAccounting() {
        var scenes=tree("scenes/en/story.json");
        ((ObjectNode)scenes.path("scenes").get(1)).set("onEnter",json.readTree("[{\"type\":\"FLAG_SET\",\"flag\":\"joined\",\"value\":true}]"));
        var fail=new java.util.concurrent.atomic.AtomicBoolean();
        Clock clock=new Clock() {
            public java.time.ZoneId getZone(){return java.time.ZoneOffset.UTC;}
            public Clock withZone(java.time.ZoneId zone){return this;}
            public java.time.Instant instant(){if(fail.getAndSet(false))throw new IllegalStateException("FIXTURE_COMMIT_FAILURE");return java.time.Instant.EPOCH;}
        };
        var pack=loader.load(id,source(Map.of("places.json",places().toString(),"scenes/en/story.json",scenes.toString())));
        var e=Engine.builder(new PackRegistry(List.of(pack))).clock(clock).ids(new UuidIdSource()).random(Randoms.seeded(2)).build();
        var run=start(e);var before=run.snapshot();fail.set(true);
        assertThat(run.apply(new Command.Move("river")).accepted()).isFalse();assertThat(run.snapshot()).isEqualTo(before);
        assertThat(run.view().characters()).isEmpty();assertThat(run.view().presentation().party()).isEmpty();
        assertThat(run.apply(new Command.Move("river")).accepted()).isTrue();
    }
    @Test void rejectsMalformedBindingsPoliciesConditionsAndLocaleHints() {
        for (String defect : List.of("missing","target","unknown","duplicate","limit","flag","condition","hint","blank","navigation","schema")) {
            var p=places();var entry=(ObjectNode)p.path("places").get(1).path("entry");
            switch(defect) {
                case "missing" -> ((ObjectNode)p.path("places").get(0)).remove("entry");
                case "target" -> entry.put("sceneId","missing");
                case "unknown" -> entry.put("script","bad");
                case "duplicate" -> ((ObjectNode)p.path("navigation")).set("allowedSceneIds",json.readTree("[\"camp\",\"camp\"]"));
                case "limit" -> {var ids=json.createArrayNode();for(int i=0;i<129;i++)ids.add("camp");((ObjectNode)p.path("navigation")).set("allowedSceneIds",ids);}
                case "flag" -> {entry.set("requires",json.readTree("{\"type\":\"FLAG_IS\",\"flag\":\"missing\",\"value\":true}"));entry.put("unmetHintKey","helper.need");}
                case "condition" -> entry.set("requires",json.readTree("{\"type\":\"FLAG_IS\",\"flag\":\"joined\",\"value\":true}"));
                case "hint" -> ((ObjectNode)p.path("navigation")).put("unavailableHintKey","missing");
                case "blank" -> ((ObjectNode)p.path("navigation")).put("unavailableHintKey","");
                case "navigation" -> ((ObjectNode)p.path("navigation")).set("allowedSceneIds",json.readTree("[\"missing\"]"));
                case "schema" -> p.put("schemaVersion",3);
            }
            assertThat(loader.tryLoad(id,source(Map.of("places.json",p.toString()))).ok()).as(defect).isFalse();
        }
    }
}
