package com.capybee.engine.api;

import com.capybee.engine.adapter.*;
import com.capybee.engine.api.json.SnapshotCodec;
import com.capybee.engine.pack.*;
import com.capybee.engine.pack.json.PackLoader;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Product IDs and routes come from the external reviewed reference inventory. */
class PublicPresentationPackTest {
    @Test void newMetadataPreservesRoutesGeneratedIdentitiesAndBothPinnedPreviousVersions() throws Exception {
        var inventory = JsonMapper.builder().build().readTree(Files.readString(Path.of("../fixtures/reference-authored-routes.json"))).path("packs").get(0);
        var id = new PackId(inventory.path("packId").asString()); var loader = new PackLoader(Engine.sectionValidators());
        var old = loader.load(id, new DirectoryPackSource(Path.of("../reference-game-packs", id.value())));
        var previous = loader.load(id, new DirectoryPackSource(Path.of("../reference-game-pack-versions", id.value(), "1.1.0")));
        var loaded = loader.tryLoad(id, new DirectoryPackSource(Path.of("../reference-game-pack-versions", id.value(), "1.2.0")));
        assertThat(loaded.issues()).isEmpty(); var current = loaded.pack().orElseThrow();
        for (String path : previous.files().keySet()) if (!path.startsWith("strings/")) assertThat(current.requireFile(path)).as(path).isEqualTo(previous.requireFile(path));
        var registry = new PackRegistry(List.of(old, previous, current));
        var engine = engine(registry);
        for (var pinned : List.of(old, previous)) {
            var legacy = engine(new PackRegistry(List.of(pinned))).start(new RunRequest(id, null, Map.of(), 4L)).run();
            assertThat(legacy.view().presentation()).isNull();
            assertThat(engine.resume(legacy.snapshot()).run().view()).isEqualTo(legacy.view());
        }
        for (boolean generated : List.of(false, true)) for (var route : inventory.path("routes")) {
            var started = engine.start(new RunRequest(id, null, Map.of(), 4L, generated));
            assertThat(started.ok()).isTrue(); var run = started.run();
            for (var command : route.path("commands")) {
                var action = switch (command.path("type").asString()) {
                    case "ADVANCE" -> new Command.Advance(); case "MOVE" -> new Command.Move(command.path("id").asString());
                    default -> new Command.Choose(command.path("id").asString());
                };
                assertThat(run.apply(action).accepted()).isTrue();
                var p = run.view().presentation(); assertThat(p).isNotNull(); assertThat(p.catalogVersion()).isEqualTo(1);
                assertThat(p.characters()).extracting(PresentationView.CharacterArt::id)
                        .containsExactlyInAnyOrderElementsOf(run.view().characters().stream().map(CharacterItem::id).toList());
                assertThat(p.party()).doesNotHaveDuplicates().containsExactlyElementsOf(p.characters().stream().filter(PresentationView.CharacterArt::recruited).map(PresentationView.CharacterArt::id).toList());
                assertThat(run.view().characters()).extracting(CharacterItem::slotId).doesNotHaveDuplicates();
                var saved = SnapshotCodec.toJson(run.snapshot());
                var resumed = engine(registry).resume(SnapshotCodec.fromJson(saved).snapshot());
                assertThat(resumed.issues()).isEmpty(); assertThat(resumed.run().view()).isEqualTo(run.view()); run = resumed.run();
            }
            assertThat(run.view().outcome()).isEqualTo(route.path("outcome").asString());
            assertThat(run.view().endReason()).isEqualTo(EndReason.COMPLETED);
        }
    }
    Engine engine(PackRegistry registry) {return Engine.builder(registry).clock(Clock.systemUTC()).ids(new UuidIdSource()).random(Randoms.seeded(4)).build();}
}
