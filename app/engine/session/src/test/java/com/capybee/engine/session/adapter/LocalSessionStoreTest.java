package com.capybee.engine.session.adapter;

import com.capybee.engine.api.PackRef;
import com.capybee.engine.api.RunSnapshot;
import com.capybee.engine.api.RunState;
import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.PackId;
import com.capybee.engine.pack.PackVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.capybee.engine.session.SessionPorts.CreateKind.CREATED;
import static com.capybee.engine.session.SessionTypes.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalSessionStoreTest {
    @TempDir Path directory;

    @Test
    void recoversNewestValidBackupAndQuarantinesCorruptPrimary() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-10-19T10:00:00Z"), ZoneOffset.UTC);
        OwnerRef owner = new OwnerRef("local-owner");
        UUID first = UUID.randomUUID();
        try (LocalSessionStore store = LocalSessionStore.open(directory, clock)) {
            store.createPreparing(new NewRun(first, owner, new PackId("test"), "1.0.0",
                    new LocaleTag("en"), Map.of(), null, false, clock.instant()));
            store.begin(first, UUID.randomUUID(), new byte[32], 0);
        }
        java.nio.file.Files.writeString(directory.resolve("session-store.json"), "{broken");

        try (LocalSessionStore recovered = LocalSessionStore.open(directory, clock)) {
            assertThat(recovered.find(first, owner)).isPresent();
            assertThat(recovered.recoveryReport().source()).isEqualTo(RecoverySource.BACKUP);
            assertThat(recovered.recoveryReport().notices()).extracting(RecoveryNotice::code)
                    .contains("RECOVERED_FROM_BACKUP");
        }
        try (var files = java.nio.file.Files.list(directory.resolve("quarantine"))) {
            assertThat(files).hasSize(1);
        }
    }

    @Test
    void persistsSnapshotAndLeaseStateAcrossInstances() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-19T10:00:00Z"), ZoneOffset.UTC);
        OwnerRef owner = new OwnerRef("local-owner"); UUID id = UUID.randomUUID();
        PackId pack = new PackId("test-pack"); LocaleTag locale = new LocaleTag("en");
        try (LocalSessionStore first = LocalSessionStore.open(directory, clock)) {
            assertThat(first.createPreparing(new NewRun(id, owner, pack, "1.0.0", locale, Map.of(), 4L, false, clock.instant())).kind()).isEqualTo(CREATED);
            Lease lease = first.tryClaim(id, owner, Duration.ofSeconds(10)).orElseThrow();
            RunSnapshot snapshot = new RunSnapshot(1, id.toString(), new PackRef(pack, new PackVersion(1, 0, 0)), locale, 4, Map.of(), clock.instant(), clock.instant(), 0, new RunState("start", List.of("start"), false, null));
            assertThat(first.saveActive(id, lease, 0, snapshot, List.of())).isTrue();
            UUID command = UUID.randomUUID();
            assertThat(first.begin(id, command, new byte[32], 0)).isEqualTo(com.capybee.engine.session.SessionPorts.InsertKind.INSERTED);
            first.complete(id, command, true, 1, List.of(new com.capybee.engine.api.Event("started", Map.of())));
            first.append(id, command, 1, List.of(new com.capybee.engine.api.Event("started", Map.of())));
            assertThat(first.readAfter(id, 0, 10)).hasSize(1);
            assertThat(first.tryReserveCall(new LedgerScope("profile", 2), new LedgerScope("global", 2), java.time.LocalDate.now(clock), id, 2, com.capybee.engine.session.MeteredTextGenerator.Priority.DEMAND, 0, 0, 0)).isTrue();
        }
        try (LocalSessionStore reopened = LocalSessionStore.open(directory, clock)) {
            StoredRun restored = reopened.find(id, owner).orElseThrow();
            assertThat(restored.status()).isEqualTo(RunStatus.ACTIVE);
            assertThat(restored.revision()).isZero();
            assertThat(restored.snapshot()).isEqualTo(new RunSnapshot(1, id.toString(), new PackRef(pack, new PackVersion(1, 0, 0)), locale, 4, Map.of(), clock.instant(), clock.instant(), 0, new RunState("start", List.of("start"), false, null)));
            assertThat(restored.pendingStart()).isNull();
            assertThat(reopened.tryClaim(id, new OwnerRef("different-owner"), Duration.ofSeconds(5))).isEmpty();
        }
    }

    @Test
    void refusesSecondOpenRunAndReleasesOnlyItsLease() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-19T10:00:00Z"), ZoneOffset.UTC);
        OwnerRef owner = new OwnerRef("local-owner");
        UUID runId = UUID.randomUUID();
        PackId pack = new PackId("test-pack");
        LocaleTag locale = new LocaleTag("en");
        try (LocalSessionStore store = LocalSessionStore.open(directory, clock)) {
            assertThat(store.createPreparing(new NewRun(runId, owner, pack, "1.0.0", locale, Map.of(),
                    null, false, clock.instant())).kind()).isEqualTo(CREATED);
            assertThat(store.createPreparing(new NewRun(UUID.randomUUID(), owner, pack, "1.0.0", locale,
                    Map.of(), null, false, clock.instant())).kind()).isEqualTo(
                    com.capybee.engine.session.SessionPorts.CreateKind.OWNER_HAS_ACTIVE);

            Lease lease = store.tryClaim(runId, owner, Duration.ofSeconds(5)).orElseThrow();
            store.releaseLease(runId, new Lease(UUID.randomUUID(), lease.until()));
            assertThat(store.tryClaim(runId, owner, Duration.ofSeconds(5))).isEmpty();
            store.releaseLease(runId, lease);
            assertThat(store.tryClaim(runId, owner, Duration.ofSeconds(5))).isPresent();
        }
    }

    @Test
    void refusesASecondOpenStoreInstance() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-19T10:00:00Z"), ZoneOffset.UTC);
        try (LocalSessionStore ignored = LocalSessionStore.open(directory, clock)) {
            assertThatThrownBy(() -> LocalSessionStore.open(directory, clock))
                    .isInstanceOf(com.capybee.engine.session.StoreException.class);
        }
    }

    @Test
    void persistsPrefetchCountersAndCommandDiagnosticsAcrossRestart() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-19T10:00:00Z"), ZoneOffset.UTC);
        OwnerRef owner = new OwnerRef("local-owner");
        UUID runId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();
        var profile = new LedgerScope("profile:local", 5);
        var global = new LedgerScope("global", 5);
        var day = java.time.LocalDate.of(2026, 10, 19);
        try (LocalSessionStore store = LocalSessionStore.open(directory, clock)) {
            store.createPreparing(new NewRun(runId, owner, new PackId("test"), "1.0.0",
                    new LocaleTag("en"), Map.of(), null, false, clock.instant()));
            assertThat(store.tryReserveCall(profile, global, day, runId, 10,
                    com.capybee.engine.session.MeteredTextGenerator.Priority.PREFETCH, 0, 0, 0)).isTrue();
            store.begin(runId, commandId, new byte[32], 0);
            store.complete(runId, commandId, true, 1, List.of(), 37L, SceneSource.PREFETCHED);
        }
        try (LocalSessionStore reopened = LocalSessionStore.open(directory, clock)) {
            assertThat(reopened.runPrefetchCalls(runId)).isEqualTo(1);
            assertThat(reopened.dayUsage(profile, day).prefetchCalls()).isEqualTo(1);
            assertThat(reopened.list(runId)).singleElement().satisfies(command -> {
                assertThat(command.latencyMillis()).isEqualTo(37L);
                assertThat(command.sceneSource()).isEqualTo(SceneSource.PREFETCHED);
            });
        }
    }

    @Test
    void commandLogReturnsBeginOrderAndLegacyCompletionHasNullDiagnostics() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-19T10:00:00Z"), ZoneOffset.UTC);
        OwnerRef owner = new OwnerRef("local-owner");
        UUID runId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        try (LocalSessionStore store = LocalSessionStore.open(directory, clock)) {
            store.createPreparing(new NewRun(runId, owner, new PackId("test"), "1.0.0",
                    new LocaleTag("en"), Map.of(), null, false, clock.instant()));
            store.begin(runId, first, new byte[32], 0);
            store.begin(runId, second, new byte[32], 0);
            store.complete(runId, first, true, 1, List.of());
            store.fail(runId, second, "FAILED_TEST");

            assertThat(store.list(runId)).extracting(CommandSummary::commandId).containsExactly(first, second);
            assertThat(store.find(runId, first).orElseThrow().latencyMillis()).isNull();
            assertThat(store.find(runId, second).orElseThrow().sceneSource()).isNull();
        }
    }

    @Test
    void migratesV1InMemoryAndWritesV3OnlyOnFirstChange() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-10-19T10:00:00Z"), ZoneOffset.UTC);
        Path storeFile = directory.resolve("session-store.json");
        try (var golden = getClass().getResourceAsStream("/session-store-v1.json")) {
            assertThat(golden).isNotNull();
            java.nio.file.Files.copy(golden, storeFile);
        }
        byte[] original = java.nio.file.Files.readAllBytes(storeFile);

        OwnerRef owner = new OwnerRef("local-owner");
        UUID runId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();
        try (LocalSessionStore store = LocalSessionStore.open(directory, clock)) {
            assertThat(store.findCurrent(new OwnerRef("local-owner"))).isEmpty();
            assertThat(java.nio.file.Files.readAllBytes(storeFile)).isEqualTo(original);
            store.createPreparing(new NewRun(runId, owner,
                    new PackId("test"), "1.0.0", new LocaleTag("en"), Map.of(), null, false,
                    clock.instant()));
            store.begin(runId, commandId, new byte[32], 0);
            store.complete(runId, commandId, true, 1, List.of(), 37L, SceneSource.PREFETCHED);
        }

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode envelope = (ObjectNode) mapper.readTree(java.nio.file.Files.readAllBytes(storeFile));
        ObjectNode legacy = (ObjectNode) envelope.get("data");
        legacy.put("format", "capybee-session-store");
        legacy.put("version", 1);
        legacy.remove("runPrefetchCalls");
        ArrayNode runs = (ArrayNode) legacy.get("runs");
        ObjectNode command = (ObjectNode) ((ObjectNode) runs.get(0).get("commands")).get(commandId.toString());
        command.remove("latencyMillis");
        command.remove("sceneSource");
        java.nio.file.Files.write(storeFile, mapper.writeValueAsBytes(legacy));
        java.nio.file.Files.deleteIfExists(directory.resolve("session-store.json.bak"));
        byte[] legacyBytes = java.nio.file.Files.readAllBytes(storeFile);

        try (LocalSessionStore reopened = LocalSessionStore.open(directory, clock)) {
            assertThat(reopened.find(runId, owner)).isPresent();
            assertThat(reopened.find(runId, commandId).orElseThrow().latencyMillis()).isNull();
            assertThat(reopened.find(runId, commandId).orElseThrow().sceneSource()).isNull();
            assertThat(java.nio.file.Files.readAllBytes(storeFile)).isEqualTo(legacyBytes);
            reopened.createPreparing(new NewRun(UUID.randomUUID(), new OwnerRef("second-owner"),
                    new PackId("test"), "1.0.0", new LocaleTag("en"), Map.of(), null, false,
                    clock.instant()));
        }

        assertThat(java.nio.file.Files.readString(storeFile)).contains("\"version\":3")
                .contains("\"storeRevision\":").contains("\"checksum\":");
        assertThat(java.nio.file.Files.readAllBytes(directory.resolve("session-store.json.bak")))
                .isEqualTo(legacyBytes);
    }

    @Test
    void distinguishesUnsupportedFutureVersionFromCorruptVersionZero() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-10-19T10:00:00Z"), ZoneOffset.UTC);
        Path storeFile = directory.resolve("session-store.json");
        String document = "\"format\":\"capybee-session-store\",\"runs\":[],"
                + "\"reservations\":{},\"runReservations\":{},\"usage\":{}";
        java.nio.file.Files.writeString(storeFile, "{" + document + ",\"version\":4}");
        assertThatThrownBy(() -> LocalSessionStore.open(directory, clock))
                .isInstanceOfSatisfying(com.capybee.engine.session.StoreException.class, failure -> {
                    assertThat(failure.code()).isEqualTo(com.capybee.engine.session.StoreException.Code.STORE_VERSION_UNSUPPORTED);
                    assertThat(failure.details()).containsEntry("found", 4).containsEntry("supported", 3);
                });

        java.nio.file.Files.writeString(storeFile, "{" + document + ",\"version\":0}");
        assertThatThrownBy(() -> LocalSessionStore.open(directory, clock))
                .isInstanceOfSatisfying(com.capybee.engine.session.StoreException.class, failure ->
                        assertThat(failure.code()).isEqualTo(com.capybee.engine.session.StoreException.Code.STORE_RECOVERY_REQUIRED));
    }
}
