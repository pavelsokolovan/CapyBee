package com.capybee.engine.session.adapter;

import com.capybee.engine.api.Event;
import com.capybee.engine.session.DynamicSnapshotFixture;
import com.capybee.engine.port.TextGenerationResult;
import com.capybee.engine.session.MeteredTextGenerator;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.capybee.engine.session.SessionPorts.CreateKind;
import static com.capybee.engine.session.SessionTypes.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemorySessionStoreTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-19T10:00:00Z"), ZoneOffset.UTC);
    private final InMemorySessionStore store = new InMemorySessionStore(clock);

    @Test
    void transactionRollsBackAllParticipatingPorts() {
        OwnerRef owner = new OwnerRef("profile");
        UUID runId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();
        store.createPreparing(new NewRun(runId, owner, new com.capybee.engine.pack.PackId("test"), "1.0.0",
                new com.capybee.engine.pack.LocaleTag("en"), Map.of(), null, false, clock.instant()));

        assertThatThrownBy(() -> store.execute(() -> {
            store.begin(runId, commandId, new byte[32], 0);
            store.append(runId, commandId, 0, List.of(new Event("temporary", Map.of())));
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(store.find(runId, commandId)).isEmpty();
        assertThat(store.readAfter(runId, 0, 10)).isEmpty();
    }

    @Test
    void enforcesOneOpenRunAndOwnerIsolation() {
        OwnerRef owner = new OwnerRef("profile");
        UUID runId = UUID.randomUUID();
        NewRun run = new NewRun(runId, owner, new com.capybee.engine.pack.PackId("test"), "1.0.0",
                new com.capybee.engine.pack.LocaleTag("en"), Map.of(), null, false, clock.instant());

        assertThat(store.createPreparing(run).kind()).isEqualTo(CreateKind.CREATED);
        assertThat(store.find(runId, new OwnerRef("other"))).isEmpty();
        assertThat(store.createPreparing(new NewRun(UUID.randomUUID(), owner, run.packId(), run.packVersion(), run.locale(),
                Map.of(), null, false, clock.instant())).kind()).isEqualTo(CreateKind.OWNER_HAS_ACTIVE);
        assertThat(store.tryClaim(runId, owner, Duration.ofSeconds(5))).isPresent();
    }

    @Test
    void activatesAndReplaysVerifiedDynamicSnapshotWithoutResettingState() {
        OwnerRef owner = new OwnerRef("dynamic-owner");
        UUID runId = UUID.randomUUID();
        var snapshot = DynamicSnapshotFixture.create(runId, clock);

        assertThat(store.activateDynamic(owner, runId, snapshot, clock.instant()).kind())
                .isEqualTo(CreateKind.CREATED);
        StoredRun created = store.find(runId, owner).orElseThrow();
        assertThat(created.status()).isEqualTo(RunStatus.ACTIVE);
        assertThat(created.packId()).isNull();
        assertThat(created.snapshot()).isEqualTo(snapshot);
        assertThat(created.generationCalls()).isEqualTo(snapshot.dynamic().totalProviderCalls());

        assertThat(store.activateDynamic(owner, runId, snapshot, clock.instant().plusSeconds(30)).kind())
                .isEqualTo(CreateKind.EXISTS_SAME_OWNER);
        assertThat(store.find(runId, owner).orElseThrow()).isEqualTo(created);
        assertThat(store.activateDynamic(new OwnerRef("other"), runId, snapshot, clock.instant()).kind())
                .isEqualTo(CreateKind.EXISTS_OTHER_OWNER);
        assertThatThrownBy(() -> store.activateDynamic(owner, UUID.randomUUID(), snapshot, clock.instant()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void replacesCurrentRunAtomicallyAndRejectsInvalidReplacementBeforeMutation() {
        OwnerRef owner = new OwnerRef("dynamic-owner");
        UUID oldId = UUID.randomUUID();
        store.createPreparing(new NewRun(oldId, owner, new com.capybee.engine.pack.PackId("test"), "1.0.0",
                new com.capybee.engine.pack.LocaleTag("en"), Map.of(), null, false, clock.instant()));
        var lease = store.tryClaim(oldId, owner, Duration.ofSeconds(5)).orElseThrow();
        var oldSnapshot = new com.capybee.engine.api.RunSnapshot(1, oldId.toString(),
                new com.capybee.engine.api.PackRef(new com.capybee.engine.pack.PackId("test"),
                        new com.capybee.engine.pack.PackVersion(1, 0, 0)),
                new com.capybee.engine.pack.LocaleTag("en"), 0, Map.of(), clock.instant(), clock.instant(), 0,
                new com.capybee.engine.api.RunState("start", List.of("start"), false, null));
        assertThat(store.saveActive(oldId, lease, 0, oldSnapshot, List.of())).isTrue();

        UUID invalidId = UUID.randomUUID();
        var replacement = DynamicSnapshotFixture.create(UUID.randomUUID(), clock);
        assertThatThrownBy(() -> store.replaceWithDynamic(owner, oldId, invalidId, replacement, clock.instant()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(store.find(oldId, owner).orElseThrow().status()).isEqualTo(RunStatus.ACTIVE);
        assertThat(store.findCurrent(owner).orElseThrow().id()).isEqualTo(oldId);

        UUID newId = UUID.fromString(replacement.runId());
        assertThat(store.replaceWithDynamic(owner, oldId, newId, replacement, clock.instant()).kind())
                .isEqualTo(CreateKind.CREATED);
        assertThat(store.find(oldId, owner).orElseThrow().status()).isEqualTo(RunStatus.SUSPENDED);
        assertThat(store.findCurrent(owner).orElseThrow().id()).isEqualTo(newId);
        assertThat(store.replaceWithDynamic(owner, oldId, newId, replacement, clock.instant()).kind())
                .isEqualTo(CreateKind.EXISTS_SAME_OWNER);
    }

    @Test
    void leasesAreExclusiveAndOutboxSequencesAreMonotonic() {
        OwnerRef owner = new OwnerRef("profile");
        UUID runId = UUID.randomUUID();
        store.createPreparing(new NewRun(runId, owner, new com.capybee.engine.pack.PackId("test"), "1.0.0",
                new com.capybee.engine.pack.LocaleTag("en"), Map.of(), null, false, clock.instant()));
        assertThat(store.tryClaim(runId, owner, Duration.ofSeconds(5))).isPresent();
        assertThat(store.tryClaim(runId, owner, Duration.ofSeconds(5))).isEmpty();

        store.append(runId, UUID.randomUUID(), 0, List.of(new Event("started", Map.of())));
        store.append(runId, UUID.randomUUID(), 1, List.of(new Event("advanced", Map.of())));
        assertThat(store.readAfter(runId, 0, 10)).extracting(OutboxEvent::seq).containsExactly(1L, 2L);
        assertThat(store.readAfter(runId, 1, 1)).extracting(OutboxEvent::seq).containsExactly(2L);
    }

    @Test
    void reservesProfileGlobalAndLifetimeRunCapsAtomically() {
        var profile = new LedgerScope("profile:meter", 3);
        var global = new LedgerScope("global", 2);
        UUID runId = UUID.randomUUID();
        LocalDate day = LocalDate.of(2026, 10, 19);

        assertThat(reserve(profile, global, day, runId, 3)).isTrue();
        assertThat(reserve(profile, global, day, runId, 3)).isTrue();
        assertThat(reserve(profile, global, day, runId, 3)).isFalse();
        store.releaseCall(profile, global, day, runId);
        assertThat(reserve(profile, global, day, runId, 3)).isTrue();
    }

        @Test
        void countsPrefetchReservationsWithoutUsageAndKeepsDailyCountsOnDelete() {
        OwnerRef owner = new OwnerRef("profile");
        UUID runId = UUID.randomUUID();
        store.createPreparing(new NewRun(runId, owner, new com.capybee.engine.pack.PackId("test"), "1.0.0",
            new com.capybee.engine.pack.LocaleTag("en"), Map.of(), null, false, clock.instant()));
        var profile = new LedgerScope("profile:meter", 1);
        var global = new LedgerScope("global", 10);
        LocalDate day = LocalDate.of(2026, 10, 19);

        assertThat(store.tryReserveCall(profile, global, day, runId, 10,
            MeteredTextGenerator.Priority.PREFETCH, 0, 0, 0)).isTrue();
        assertThat(store.tryReserveCall(profile, global, day, runId, 10,
            MeteredTextGenerator.Priority.PREFETCH, 0, 0, 0)).isFalse();
        assertThat(store.runPrefetchCalls(runId)).isEqualTo(1);
        assertThat(store.dayUsage(profile, day).prefetchCalls()).isEqualTo(1);

            store.recordUsage(profile, global, day, runId,
                new TextGenerationResult.TokenUsage(7, 3, 0, 0),
            MeteredTextGenerator.Priority.PREFETCH);
        assertThat(store.dayUsage(profile, day)).isEqualTo(new DayUsage(1, 7, 3, 1));
        store.delete(runId, owner);
        assertThat(store.runPrefetchCalls(runId)).isZero();
        assertThat(store.dayUsage(profile, day).prefetchCalls()).isEqualTo(1);
        }

        @Test
        void listsCommandsInBeginOrderWithOptionalCompletionDiagnostics() {
        UUID runId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        store.begin(runId, first, new byte[32], 0);
        store.begin(runId, second, new byte[32], 0);
        store.complete(runId, first, true, 1, List.of(), 12L, SceneSource.INLINE_GENERATED);
        store.complete(runId, second, false, 0, List.of());

        assertThat(store.list(runId)).extracting(CommandSummary::commandId).containsExactly(first, second);
        assertThat(store.find(runId, first).orElseThrow().latencyMillis()).isEqualTo(12L);
        assertThat(store.find(runId, first).orElseThrow().sceneSource()).isEqualTo(SceneSource.INLINE_GENERATED);
        assertThat(store.find(runId, second).orElseThrow().latencyMillis()).isNull();
        }

    private boolean reserve(LedgerScope profile, LedgerScope global, LocalDate day,
                            UUID runId, int runLimit) {
        return store.tryReserveCall(profile, global, day, runId, runLimit,
                com.capybee.engine.session.MeteredTextGenerator.Priority.DEMAND, 0, 0, 0);
    }
}
