package com.capybee.engine.session.adapter;

import com.capybee.engine.api.Event;
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

class InMemorySessionStoreTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-19T10:00:00Z"), ZoneOffset.UTC);
    private final InMemorySessionStore store = new InMemorySessionStore(clock);

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

    private boolean reserve(LedgerScope profile, LedgerScope global, LocalDate day,
                            UUID runId, int runLimit) {
        return store.tryReserveCall(profile, global, day, runId, runLimit,
                com.capybee.engine.session.MeteredTextGenerator.Priority.DEMAND, 0, 0, 0);
    }
}
