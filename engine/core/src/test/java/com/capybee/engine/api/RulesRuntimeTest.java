package com.capybee.engine.api;

import com.capybee.engine.adapter.InMemoryRunStore;
import com.capybee.engine.adapter.Randoms;
import com.capybee.engine.adapter.SequentialIdSource;
import com.capybee.engine.pack.DirectoryPackSource;
import com.capybee.engine.pack.Pack;
import com.capybee.engine.pack.PackId;
import com.capybee.engine.pack.PackVersion;
import com.capybee.engine.pack.PackRegistry;
import com.capybee.engine.pack.json.PackLoader;
import com.capybee.engine.api.json.SnapshotCodec;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RulesRuntimeTest {
    private static final PackId PACK_ID = new PackId("fixture-text-dungeon");
        private static final ObjectMapper MAPPER = new ObjectMapper();

        private Pack pack() {
        var source = new DirectoryPackSource(Path.of("..", "fixtures", "game-packs", "fixture-text-dungeon"));
                return new PackLoader(Engine.sectionValidators()).load(PACK_ID, source);
        }

        private Engine engine() {
                return engine(pack());
        }

        private Engine engine(Pack pack) {
                return Engine.builder(new PackRegistry(List.of(pack)))
                .clock(Clock.fixed(Instant.parse("2026-10-12T10:00:00Z"), ZoneOffset.UTC))
                .random(Randoms.seeded(42))
                .ids(new SequentialIdSource("run"))
                .build();
    }

    private Run start(Engine engine) {
        StartResult result = engine.start(new RunRequest(PACK_ID, null, Map.of(), 42L));
        assertThat(result.ok()).isTrue();
        assertThat(result.events()).isEmpty();
        return result.run();
    }

    @Test
    void disabledChoiceDoesNotChangeSnapshot() {
        Run run = start(engine());
        assertThat(run.apply(new Command.Choose("corridor")).accepted()).isTrue();
        assertThat(run.apply(new Command.Choose("right")).accepted()).isTrue();
        assertThat(run.view().options()).filteredOn(Option::id, "climb")
                .singleElement().extracting(Option::enabled).isEqualTo(false);
        assertThat(run.view().options()).filteredOn(Option::id, "climb")
                .singleElement().extracting(Option::hint).isEqualTo("You need the key.");

        String before = SnapshotCodec.toJson(run.snapshot());
        StepResult rejected = run.apply(new Command.Choose("climb"));

        assertThat(rejected.accepted()).isFalse();
        assertThat(rejected.issues()).extracting(Issue::code).contains("OPTION_DISABLED");
        assertThat(SnapshotCodec.toJson(run.snapshot())).isEqualTo(before);
    }

    @Test
    void winningPathAppliesEffectsCompletesObjectiveEmitsEventAndRoundTrips() {
        Engine engine = engine();
        Run run = start(engine);
        run.apply(new Command.Choose("corridor"));
        run.apply(new Command.Choose("left"));
        run.apply(new Command.Choose("back"));
        run.apply(new Command.Choose("right"));
        StepResult finalStep = run.apply(new Command.Choose("climb"));

        assertThat(finalStep.accepted()).isTrue();
        assertThat(finalStep.events()).containsExactly(new Event("run.completed", Map.of("outcome", "escaped", "health", "8")));
        assertThat(finalStep.view().status()).containsExactly(new StatusItem("health", "Health", "8 / 10"));
        assertThat(finalStep.view().objectives()).containsExactly(new ObjectiveItem("escape", "Escape", ObjectiveState.DONE));
        assertThat(finalStep.view().outcome()).isEqualTo("escaped");
        assertThat(run.snapshot().state().visited()).containsExactly("cell", "corridor", "armory", "stairs", "exit");

        String json = SnapshotCodec.toJson(run.snapshot());
        var parsed = SnapshotCodec.fromJson(json);
        assertThat(parsed.issues()).isEmpty();
        assertThat(SnapshotCodec.toJson(parsed.snapshot())).isEqualTo(json);
        var resumed = engine.resume(parsed.snapshot());
        assertThat(resumed.ok()).isTrue();
        assertThat(resumed.run().snapshot().state()).isEqualTo(run.snapshot().state());
    }

    @Test
    void olderSnapshotGetsCurrentDefaultsAndSameMajorMigrationPrunesAndClamps() {
        Engine engine = engine();
        Run started = start(engine);
        RunSnapshot current = started.snapshot();
        RunState oldState = new RunState("cell", List.of("cell"), false, null);
        RunSnapshot legacy = new RunSnapshot(1, current.runId(), current.pack(), current.locale(), current.seed(),
                current.startInputs(), current.createdAt(), current.updatedAt(), 0, oldState);
        var legacyResume = engine.resume(legacy);
        assertThat(legacyResume.ok()).isTrue();
        assertThat(legacyResume.run().snapshot().state().counters()).containsEntry("health", 10);
        assertThat(legacyResume.run().snapshot().state().flags()).containsEntry("has-key", false);
        assertThat(legacyResume.run().snapshot().state().sets()).containsEntry("inventory", List.of());
        assertThat(legacyResume.run().snapshot().state().objectives()).containsEntry("escape", ObjectiveState.ACTIVE);

        RunState oldVersionState = new RunState("stairs", List.of("cell", "removed-room", "stairs"), false, null,
                Map.of("health", 15, "removed-counter", 2),
                Map.of("has-key", true, "removed-flag", false),
                Map.of("inventory", List.of("key", "removed-member"), "removed-set", List.of("member")),
                Map.of("escape", ObjectiveState.ACTIVE, "removed-objective", ObjectiveState.DONE), "removed-outcome");
        RunSnapshot oldVersion = new RunSnapshot(1, current.runId(),
                new PackRef(PACK_ID, new PackVersion(1, 0, 0)), current.locale(), current.seed(), current.startInputs(),
                current.createdAt(), current.updatedAt(), 2, oldVersionState);
        var migrated = engine.resume(oldVersion);

        assertThat(migrated.ok()).isTrue();
        assertThat(migrated.issues()).extracting(Issue::code)
                .contains("SNAPSHOT_STATE_PRUNED", "SNAPSHOT_STATE_CLAMPED");
        assertThat(migrated.run().snapshot().state().counters()).containsEntry("health", 10)
                .doesNotContainKey("removed-counter");
        assertThat(migrated.run().snapshot().state().sets()).containsEntry("inventory", List.of("key"))
                .doesNotContainKey("removed-set");
        assertThat(migrated.run().snapshot().state().visited()).containsExactly("cell", "stairs");
        assertThat(migrated.run().snapshot().state().outcome()).isNull();
    }

    @Test
    void exactVersionRejectsInvalidCounterState() {
        Engine engine = engine();
        RunSnapshot current = start(engine).snapshot();
        RunState invalid = new RunState("cell", List.of("cell"), false, null,
                Map.of("health", 11), Map.of(), Map.of(), Map.of(), null);
        RunSnapshot snapshot = new RunSnapshot(1, current.runId(), current.pack(), current.locale(), current.seed(),
                current.startInputs(), current.createdAt(), current.updatedAt(), 0, invalid);

        var result = engine.resume(snapshot);

        assertThat(result.ok()).isFalse();
        assertThat(result.run()).isNull();
        assertThat(result.issues()).extracting(Issue::code).contains("SNAPSHOT_INVALID");
    }

    @Test
    void inMemoryRunStoreLoadsReplacesDeletesAndRejectsNulls() {
        InMemoryRunStore store = new InMemoryRunStore();
        RunSnapshot first = start(engine()).snapshot();
        RunSnapshot second = new RunSnapshot(first.snapshotVersion(), first.runId(), first.pack(), first.locale(),
                first.seed(), first.startInputs(), first.createdAt(), first.updatedAt(), first.step(), first.state());

        assertThat(store.load(first.runId())).isEmpty();
        store.save(first);
        store.save(second);
        assertThat(store.load(first.runId())).contains(second);
        store.delete(first.runId());
        store.delete(first.runId());
        assertThat(store.load(first.runId())).isEmpty();
        assertThatThrownBy(() -> store.load(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.delete(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.save(null)).isInstanceOf(NullPointerException.class);
    }

        @Test
        void orderedEffectsAndEndRunPreserveTheLastExplicitOutcome() throws Exception {
                Pack base = pack();
                Map<String, String> files = new LinkedHashMap<>(base.files());
                ObjectNode rules = (ObjectNode) MAPPER.readTree(files.get("rules.json"));
                ArrayNode sets = (ArrayNode) rules.get("sets");
                ((ObjectNode) sets.get(0)).set("initial", MAPPER.createArrayNode().add("key"));
                files.put("rules.json", rules.toString());

                ObjectNode scenes = (ObjectNode) MAPPER.readTree(files.get("scenes/en/dungeon.json"));
                ObjectNode cell = scene(scenes, "cell");
                ObjectNode firstOption = (ObjectNode) ((ArrayNode) cell.get("options")).get(0);
                ArrayNode effects = MAPPER.createArrayNode();
                effects.add(effect("{\"type\":\"COUNTER_SET\",\"counter\":\"health\",\"value\":5}"));
                effects.add(effect("{\"type\":\"COUNTER_ADD\",\"counter\":\"health\",\"delta\":2}"));
                effects.add(effect("{\"type\":\"FLAG_SET\",\"flag\":\"has-key\",\"value\":true}"));
                effects.add(effect("{\"type\":\"SET_ADD\",\"set\":\"inventory\",\"value\":\"key\"}"));
                effects.add(effect("{\"type\":\"SET_ADD\",\"set\":\"inventory\",\"value\":\"token\"}"));
                effects.add(effect("{\"type\":\"SET_REMOVE\",\"set\":\"inventory\",\"value\":\"key\"}"));
                effects.add(effect("{\"type\":\"EMIT_EVENT\",\"event\":\"run.completed\",\"payload\":{\"outcome\":\"escaped\",\"health\":{\"counter\":\"health\"}}}"));
                effects.add(effect("{\"type\":\"END_RUN\",\"outcome\":\"escaped\"}"));
                effects.add(effect("{\"type\":\"END_RUN\"}"));
                firstOption.set("effects", effects);
                files.put("scenes/en/dungeon.json", scenes.toString());
                Engine engine = engine(new Pack(base.manifest(), files));
                Run run = start(engine);

                StepResult step = run.apply(new Command.Choose("corridor"));

                assertThat(step.accepted()).isTrue();
                assertThat(step.view().ended()).isTrue();
                assertThat(step.view().outcome()).isEqualTo("escaped");
                assertThat(step.view().status()).containsExactly(new StatusItem("health", "Health", "7 / 10"));
                assertThat(run.snapshot().state().flags()).containsEntry("has-key", true);
                assertThat(run.snapshot().state().sets()).containsEntry("inventory", List.of("token"));
                assertThat(step.events()).containsExactly(new Event("run.completed", Map.of("outcome", "escaped", "health", "7")));
        }

        @Test
        void startupEvaluatesObjectivesToFixedPointAndReturnsStartupEvents() throws Exception {
                Pack base = pack();
                Map<String, String> files = new LinkedHashMap<>(base.files());
                ObjectNode rules = (ObjectNode) MAPPER.readTree(files.get("rules.json"));
                ArrayNode objectives = MAPPER.createArrayNode();
                objectives.add(MAPPER.readTree("{\"id\":\"first\",\"labelKey\":\"objective.escape\","
                                + "\"completeWhen\":{\"type\":\"FLAG_IS\",\"flag\":\"has-key\",\"value\":true},\"onComplete\":[]}"));
                objectives.add(MAPPER.readTree("{\"id\":\"second\",\"labelKey\":\"objective.escape\","
                                + "\"completeWhen\":{\"type\":\"STEP_AT_LEAST\",\"step\":0},\"onComplete\":["
                                + "{\"type\":\"FLAG_SET\",\"flag\":\"has-key\",\"value\":true},"
                                + "{\"type\":\"EMIT_EVENT\",\"event\":\"run.completed\","
                                + "\"payload\":{\"outcome\":\"escaped\",\"health\":{\"counter\":\"health\"}}}]}"));
                rules.set("objectives", objectives);
                files.put("rules.json", rules.toString());
                Engine engine = engine(new Pack(base.manifest(), files));

                StartResult started = engine.start(new RunRequest(PACK_ID, null, Map.of(), 42L));

                assertThat(started.ok()).isTrue();
                assertThat(started.events()).containsExactly(new Event("run.completed", Map.of("outcome", "escaped", "health", "10")));
                assertThat(started.run().view().objectives()).extracting(ObjectiveItem::state)
                                .containsExactly(ObjectiveState.DONE, ObjectiveState.DONE);
        }

        @Test
        void linearSceneBranchesSelectFirstMatchingTargetUsingAcceptedStepCount() throws Exception {
                Pack base = pack();
                Map<String, String> files = new LinkedHashMap<>(base.files());
                ObjectNode scenes = (ObjectNode) MAPPER.readTree(files.get("scenes/en/dungeon.json"));
                ObjectNode listen = scene(scenes, "listen");
                ArrayNode branches = MAPPER.createArrayNode();
                branches.add(MAPPER.readTree("{\"if\":{\"type\":\"STEP_AT_LEAST\",\"step\":2},\"next\":\"armory\"}"));
                branches.add(MAPPER.readTree("{\"if\":{\"type\":\"STEP_AT_LEAST\",\"step\":1},\"next\":\"stairs\"}"));
                listen.set("branches", branches);
                files.put("scenes/en/dungeon.json", scenes.toString());
                Run run = start(engine(new Pack(base.manifest(), files)));
                run.apply(new Command.Choose("listen"));

                StepResult advanced = run.apply(new Command.Advance());

                assertThat(advanced.accepted()).isTrue();
                assertThat(run.snapshot().state().sceneId()).isEqualTo("armory");
                assertThat(run.snapshot().step()).isEqualTo(2);
        }

        private static ObjectNode scene(ObjectNode root, String id) {
                ArrayNode scenes = (ArrayNode) root.get("scenes");
                for (int index = 0; index < scenes.size(); index++) {
                        ObjectNode scene = (ObjectNode) scenes.get(index);
                        if (scene.get("id").asString().equals(id)) return scene;
                }
                throw new IllegalArgumentException("Missing test scene: " + id);
        }

        private static ObjectNode effect(String json) throws Exception {
                return (ObjectNode) MAPPER.readTree(json);
        }
}