package com.capybee.engine.api;

import com.capybee.engine.adapter.*;
import com.capybee.engine.api.json.SnapshotCodec;
import com.capybee.engine.content.json.PresentationParser;
import com.capybee.engine.pack.*;
import com.capybee.engine.pack.json.PackLoader;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.ArrayNode;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.*;

class PresentationContractTest {
    final PackId id = new PackId("fixture-presentation");
    final Path root = Path.of("../fixtures/game-packs", id.value());
    final JsonMapper json = JsonMapper.builder().build();
    final PackLoader loader = new PackLoader(Engine.sectionValidators());
    Pack pack() {return loader.load(id, new DirectoryPackSource(root));}
    Engine engine(Pack pack) {return Engine.builder(new PackRegistry(List.of(pack))).clock(Clock.systemUTC()).ids(new UuidIdSource()).random(Randoms.seeded(4)).build();}
    Run run(Engine engine) {var started = engine.start(new RunRequest(id, null, Map.of(), 4L)); assertThat(started.issues()).isEmpty(); return started.run();}

    @Test void explicitSourceIdentityIsManifestBoundWhileHostExpectedIdentityRemainsStrict() throws Exception {
        var source = new DirectoryPackSource(root);
        assertThat(loader.tryLoad(source).pack().orElseThrow().manifest().id()).isEqualTo(id);
        assertThat(loader.tryLoad(new PackId("different-pack"), source).issues()).anyMatch(i -> i.code().equals("PACK_ID_MISMATCH"));
        for (var raw : List.of("{}", "[]", "{", "{\"id\":\"1.0.0\"}", " ".repeat(PackLayout.MAX_FILE_BYTES + 1))) {
            assertThat(loader.tryLoad(new InMemoryPackSource(Map.of("pack.json", raw))).ok()).isFalse();
        }
        assertThat(loader.tryLoad(new InMemoryPackSource(Map.of())).issues()).anyMatch(i -> i.code().equals("PACK_MANIFEST_MISSING"));
    }

    @Test void movementCannotRevealCastAndRecruitmentComesOnlyFromAcceptedFlags() {
        var engine = engine(pack()); var run = run(engine);
        assertThat(run.view().characters()).isEmpty(); assertThat(run.view().presentation().party()).isEmpty();
        assertThat(run.view().toString()).doesNotContain("Traveler", "helper.", "joined", "character.traveler");
        var initial = run.view().presentation();
        assertThat(run.apply(new Command.Move("river")).accepted()).isTrue();
        assertThat(run.view().presentation()).isEqualTo(initial);
        assertThat(run.view().characters()).isEmpty();
        var before = SnapshotCodec.toJson(run.snapshot());
        assertThat(run.apply(new Command.Choose("hidden-choice")).accepted()).isFalse();
        assertThat(SnapshotCodec.toJson(run.snapshot())).isEqualTo(before);
        assertThat(run.apply(new Command.Choose("meet")).accepted()).isTrue();
        var known = run.view().presentation().characters().getFirst();
        assertThat(known.trait()).isEqualTo("Patient"); assertThat(known.need()).isEqualTo("Needs a quiet place.");
        assertThat(known.recruited()).isFalse(); assertThat(run.view().characters().getFirst().inCurrentScene()).isTrue();
        assertThat(run.view().presentation().backdropAssetId()).isEqualTo("backdrop.river");
        assertThat(run.apply(new Command.Choose("decline")).accepted()).isTrue();
        assertThat(run.view().presentation().party()).isEmpty(); assertThat(run.view().characters().getFirst().inCurrentScene()).isFalse();
        assertThat(run.apply(new Command.Choose("meet")).accepted()).isTrue();
        assertThat(run.apply(new Command.Choose("help")).accepted()).isTrue();
        assertThat(run.view().presentation().party()).containsExactly(known.id());
        assertThat(run.view().presentation().characters().getFirst().recruited()).isTrue();
        assertThat(run.apply(new Command.Advance()).accepted()).isTrue();
        assertThat(run.apply(new Command.Choose("meet")).accepted()).isTrue();
        assertThat(run.apply(new Command.Choose("help")).accepted()).isTrue();
        assertThat(run.view().presentation().party()).containsExactly(known.id());
        var snapshot = SnapshotCodec.toJson(run.snapshot());
        assertThat(snapshot).doesNotContain("\"presentation\"", "\"assetId\"", "\"traitKey\"");
        assertThat(engine(pack()).resume(SnapshotCodec.fromJson(snapshot).snapshot()).run().view()).isEqualTo(run.view());
        assertThatThrownBy(() -> run.view().presentation().party().add("forged")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> run.view().presentation().characters().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> run.view().presentation().places().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(run.apply(new Command.Advance()).accepted()).isTrue();
        assertThat(run.apply(new Command.Choose("rest")).accepted()).isTrue();
        assertThat(run.view().ended()).isTrue(); assertThat(run.view().presentation().party()).containsExactly(known.id());
        var abandoned = run(engine); abandoned.apply(new Command.Choose("meet")); abandoned.apply(new Command.Abandon());
        assertThat(abandoned.view().presentation()).isNull(); assertThat(abandoned.view().characters()).isEmpty();
    }

    @Test void projectionPreservesAcceptedGeneratedIdentityAndNeverDisclosesUnseenDefinitions() {
        var base = PresentationParser.parse(pack());
        var c = base.characters().getFirst();
        var character = new com.capybee.engine.content.PresentationDefinition.CharacterDef(c.id(), "traveler-slot", c.nameKey(),
                c.descriptionKey(), null, null, c.assetId(), c.sceneIds(), c.recruitedFlag());
        var definition = new com.capybee.engine.content.PresentationDefinition(1, null, List.of(character), List.of(), base.places());
        var member = new CharacterItem("engine-owned-id", "traveler-slot", "Accepted name", "Accepted description", true);
        var flags = new HashMap<>(Map.of("joined", true)); var saved = Map.copyOf(flags);
        var hidden = PresentationProjector.project(definition, "camp", List.of("camp"), flags, Map.of("helper.name", "Fallback", "helper.description", "Fallback"), List.of(), Set.of());
        assertThat(hidden.characters()).isEmpty(); assertThat(hidden.presentation().characters()).isEmpty(); assertThat(hidden.presentation().party()).isEmpty();
        var projected = PresentationProjector.project(definition, "visitor", List.of("camp"), flags, Map.of(), List.of(member), Set.of());
        assertThat(projected.characters()).containsExactly(member);
        assertThat(projected.presentation().characters().getFirst().id()).isEqualTo(member.id());
        assertThat(projected.presentation().characters().getFirst().trait()).isNull();
        assertThat(projected.presentation().party()).containsExactly(member.id()); assertThat(projected.presentation().places()).isEmpty();
        assertThat(flags).isEqualTo(saved);
        assertThat(PresentationProjector.project(null, "camp", List.of(), flags, Map.of(), List.of(member), Set.of()).presentation()).isNull();
        assertThatThrownBy(() -> base.characters().getFirst().sceneIds().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void shortRepliesRetainTheAcceptedPresentationAndDoNotCreatePartyMembers() throws Exception {
        var files = new HashMap<>(pack().files());
        var manifest = (ObjectNode) json.readTree(Files.readString(root.resolve("pack.json")));
        ((ObjectNode) manifest.get("contentPolicy")).put("freeTextAllowed", true);
        files.put("pack.json", manifest.toString());
        var story = (ObjectNode) json.readTree(files.get("scenes/en/story.json"));
        ((ObjectNode) story.get("scenes").get(0)).putObject("input").put("mode", "CHOICE_OR_TEXT").put("maxChars", 100);
        files.put("scenes/en/story.json", story.toString());
        var pack = loader.load(id, new InMemoryPackSource(files));
        var engine = Engine.builder(new PackRegistry(List.of(pack))).clock(Clock.systemUTC()).ids(new UuidIdSource()).random(Randoms.seeded(4))
                .inputGuard((input, policy) -> new com.capybee.engine.input.InputSafetyDecision(com.capybee.engine.input.InputSafetyAction.ALLOW, null, "OK"))
                .inputInterpreter((input, context) -> new com.capybee.engine.input.InputResolution.ShortReply("A gentle answer.")).build();
        var run = run(engine); var before = run.view().presentation();
        var reply = run.apply(new Command.SubmitText("May we pause?"));
        assertThat(reply.accepted()).isTrue(); assertThat(reply.view().presentation()).isEqualTo(before);
        assertThat(reply.view().presentation().party()).isEmpty();
    }

    @Test void malformedJsonDuplicateFieldsAndLegacyAssetDeclarationsHaveExplicitBehavior() throws Exception {
        var files = new HashMap<>(pack().files()); files.put("pack.json", Files.readString(root.resolve("pack.json")));
        for (String raw : List.of("{", "[]", "{\"schemaVersion\":1,\"schemaVersion\":2}", files.get("presentation.json") + " {}")) {
            var changed = new HashMap<>(files); changed.put("presentation.json", raw);
            assertThat(loader.tryLoad(id, new InMemoryPackSource(changed)).ok()).isFalse();
            assertThatThrownBy(() -> PresentationParser.parse(new Pack(pack().manifest(), changed))).hasMessage("PRESENTATION_INVALID");
        }
        var legacy = new HashMap<>(files); legacy.remove("presentation.json"); legacy.remove("assets/catalog.json");
        var manifest = (ObjectNode) json.readTree(legacy.get("pack.json"));
        var indexed = manifest.putArray("files"); legacy.keySet().stream().filter(k -> !k.equals("pack.json")).sorted().forEach(indexed::add);
        legacy.put("pack.json", manifest.toString());
        var loaded = loader.tryLoad(id, new InMemoryPackSource(legacy)); assertThat(loaded.issues()).isEmpty();
        assertThat(PresentationParser.parse(loaded.pack().orElseThrow())).isNull();
    }

    LoadResult mutate(String file, Consumer<ObjectNode> edit) throws Exception {
        Map<String, String> files = new HashMap<>();
        try (var paths = Files.walk(root)) {for (Path path : paths.filter(Files::isRegularFile).toList()) files.put(root.relativize(path).toString().replace('\\', '/'), Files.readString(path));}
        var node = (ObjectNode) json.readTree(files.get(file)); edit.accept(node); files.put(file, node.toString());
        return loader.tryLoad(id, new InMemoryPackSource(files));
    }
    ObjectNode firstCharacter(ObjectNode node) {return (ObjectNode) node.path("characters").get(0);}

    @Test void rejectsMalformedUnsupportedDuplicateWrongKindAndDanglingMetadata() throws Exception {
        var edits = List.<Consumer<ObjectNode>>of(
            n -> n.put("schemaVersion", 2), n -> n.put("schemaVersion", "1"), n -> n.put("unknown", "raw"),
            n -> n.put("characters", "bad"), n -> n.putNull("scenes"), n -> n.remove("places"),
            n -> firstCharacter(n).put("assetId", "terrain.camp"), n -> firstCharacter(n).put("assetId", "https://example.invalid/a"),
            n -> firstCharacter(n).remove("nameKey"), n -> firstCharacter(n).put("nameKey", "missing.key"),
            n -> firstCharacter(n).put("recruitedFlag", "missing-flag"), n -> firstCharacter(n).put("slotId", "missing-slot"),
            n -> firstCharacter(n).put("sceneIds", "bad"), n -> firstCharacter(n).putArray("sceneIds").add("missing-scene"),
            n -> firstCharacter(n).putArray("sceneIds"), n -> firstCharacter(n).putArray("sceneIds").add("visitor").add("visitor"),
            n -> ((ArrayNode) n.get("characters")).add(firstCharacter(n).deepCopy()),
            n -> ((ArrayNode) n.get("scenes")).add(n.get("scenes").get(0).deepCopy()),
            n -> ((ArrayNode) n.get("places")).add(n.get("places").get(0).deepCopy()),
            n -> ((ObjectNode) n.get("places").get(0)).put("placeId", "missing-place"),
            n -> firstCharacter(n).put("id", "x".repeat(129)),
            n -> {var entries = (ArrayNode) n.get("characters"); for(int i=0;i<33;i++) entries.add(entries.get(0).deepCopy());}
        );
        for (var edit : edits) {var result = mutate("presentation.json", edit); assertThat(result.ok()).isFalse(); assertThat(result.issues()).isNotEmpty();}
        for (var edit : List.<Consumer<ObjectNode>>of(n -> n.put("catalogVersion", 0), n -> n.put("schemaVersion", 3),
                n -> ((ObjectNode)n.get("assets").get(0)).put("id", "../remote.svg"),
                n -> ((ObjectNode)n.get("assets").get(0)).put("kind", "HTML"),
                n -> ((ArrayNode)n.get("assets")).add(n.get("assets").get(0).deepCopy()))) {
            assertThat(mutate("assets/catalog.json", edit).ok()).isFalse();
        }
        assertThat(mutate("strings/en.json", n -> n.put("helper.need", "x".repeat(513))).ok()).isFalse();
        assertThat(mutate("pack.json", n -> n.putArray("features").add("PLACES")).ok()).isFalse();
        assertThat(mutate("pack.json", n -> {var files=(ArrayNode)n.get("files");for(int i=0;i<files.size();i++)if(files.get(i).asString().equals("assets/catalog.json")){files.remove(i);break;}}).ok()).isFalse();
    }
}
