package com.capybee.engine.api;

import com.capybee.engine.adapter.*;
import com.capybee.engine.pack.*;
import com.capybee.engine.pack.json.PackLoader;
import com.capybee.engine.port.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Concrete content identifiers come from the reviewed board inventory. */
class IllustratedPrefetchTest {
 @Test void disabledChoicesAreNotPrefetched() {
  var id=new PackId("fixture-generated");var directory=new DirectoryPackSource(Path.of("../fixtures/game-packs",id.value()));
  var json=new ObjectMapper();var scenes=json.readTree(directory.read("scenes/en/story.json").orElseThrow());
  ((tools.jackson.databind.node.ObjectNode)scenes.path("scenes").get(0).path("options").get(0)).set("requires",json.readTree("{\"type\":\"STEP_AT_LEAST\",\"step\":50}"));
  PackSource source=new PackSource(){public Optional<String> read(String path){return path.equals("scenes/en/story.json")?Optional.of(scenes.toString()):directory.read(path);}public String describe(){return "disabled-prefetch-fixture";}};
  var pack=new PackLoader(Engine.sectionValidators()).load(id,source);
  var engine=Engine.builder(new PackRegistry(List.of(pack))).clock(Clock.systemUTC()).ids(new UuidIdSource()).random(Randoms.seeded(4)).build();
  var run=engine.start(new RunRequest(id,null,Map.of(),4L,true)).run();
  assertThat(run.view().options().getFirst().enabled()).isFalse();assertThat(run.prefetchCandidates()).isEmpty();
 }
 @Test void sceneRequestsCarryThemeAndTrustedArtAndPrefetchNeverEntersItsDestination() throws Exception {
  var json=new ObjectMapper();var spec=json.readTree(Files.readString(Path.of("../fixtures/reference-board-routes.json")));
  var id=new PackId(spec.path("packId").asString());var loader=new PackLoader(Engine.sectionValidators());
  var loaded=loader.tryLoad(id,new DirectoryPackSource(Path.of("../reference-game-pack-versions",id.value(),"1.4.0")));
  assertThat(loaded.issues()).isEmpty();var pack=loaded.pack().orElseThrow();
  var requests=new ArrayList<StructuredGenerationRequest>();
  var engine=Engine.builder(new PackRegistry(List.of(pack))).clock(Clock.systemUTC()).ids(new UuidIdSource()).random(Randoms.seeded(4))
   .textGenerator(request->{requests.add(request);throw new TextGenerationException(TextGenerationException.Code.UNAVAILABLE);}).build();
  var run=engine.start(new RunRequest(id,null,Map.of(),4L,true)).run();assertThat(run).isNotNull();
  // Arrival's Continue target is eligible, but map navigation remains gated.
  assertThat(run.view().places().availableMoves()).isEmpty();assertThat(run.prefetchCandidates()).hasSize(1);
  var before=run.view();var scene=run.snapshot().state().sceneId();var step=run.snapshot().step();
  run.prefetch(run.prefetchCandidates().getFirst());
  assertThat(run.view()).isEqualTo(before);assertThat(run.snapshot().state().sceneId()).isEqualTo(scene);assertThat(run.snapshot().step()).isEqualTo(step);
  assertThat(run.apply(new Command.Advance()).accepted()).isTrue();
  var candidates=run.prefetchCandidates();assertThat(candidates).doesNotHaveDuplicates();
  var definitions=com.capybee.engine.places.json.PlacesParser.parse(pack);
  for(var place:definitions.places())if(run.view().places().availableMoves().contains(place.id())) {
   assertThat(candidates).contains(place.entry().sceneId());
  }
  String hub=run.view().places().currentPlaceId();
  var visitor=definitions.places().stream().filter(p->run.view().places().availableMoves().contains(p.id()))
   .filter(p->definitions.links().stream().anyMatch(l->l.from().equals(p.id())&&!l.to().equals(hub)||l.bidirectional()&&l.to().equals(p.id())&&!l.from().equals(hub)))
   .findFirst().orElseThrow();
  assertThat(run.apply(new Command.Move(visitor.id())).accepted()).isTrue();
  var neighbor=definitions.places().stream().filter(p->run.view().places().availableMoves().contains(p.id())&&!p.id().equals(hub)).findFirst().orElseThrow();
  assertThat(run.prefetchCandidates()).contains(neighbor.entry().sceneId());
  before=run.view();run.prefetch(neighbor.entry().sceneId());assertThat(run.view()).isEqualTo(before);
  var sceneRequests=requests.stream().map(r->json.readTree(r.untrustedDataJson())).filter(n->n.path("stage").asString().equals("SCENE")).toList();
  assertThat(sceneRequests).isNotEmpty();
  var art=com.capybee.engine.content.json.PresentationParser.parse(pack);
  for(var request:sceneRequests){
   assertThat(request.has("theme")).isTrue();var trusted=request.path("trustedIllustration");
   assertThat(trusted.path("playerAssetId").asString()).isEqualTo(art.playerAssetId());
   var expected=art.characters().stream().filter(c->c.sceneIds().contains(request.path("sceneId").asString())).map(c->c.assetId()).distinct().toList();
   assertThat(trusted.path("characterAssetIds").valueStream().map(n->n.asString()).toList()).isEqualTo(expected);
   assertThat(trusted.toString()).doesNotContain("recruitedFlag","sceneIds");
  }
  var restored=Engine.builder(new PackRegistry(List.of(pack))).clock(Clock.systemUTC()).ids(new UuidIdSource()).random(Randoms.seeded(4)).build().resume(run.snapshot());
  assertThat(restored.run().view()).isEqualTo(run.view());
 }
}
