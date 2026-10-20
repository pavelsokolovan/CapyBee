package com.capybee.engine.session.adapter;

import com.capybee.engine.api.PackRef;
import com.capybee.engine.api.RunSnapshot;
import com.capybee.engine.api.RunState;
import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.PackId;
import com.capybee.engine.pack.PackVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    void persistsAnActiveRunAndRejectsASecondOpener() {
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
            assertThatThrownBy(() -> LocalSessionStore.open(directory, clock)).isInstanceOf(com.capybee.engine.session.StoreException.class);
        }
        try (LocalSessionStore reopened = LocalSessionStore.open(directory, clock)) {
            assertThat(reopened.find(id, owner)).isPresent().get().extracting(StoredRun::snapshot).isEqualTo(new RunSnapshot(1, id.toString(), new PackRef(pack, new PackVersion(1, 0, 0)), locale, 4, Map.of(), clock.instant(), clock.instant(), 0, new RunState("start", List.of("start"), false, null)));
        }
    }
}
