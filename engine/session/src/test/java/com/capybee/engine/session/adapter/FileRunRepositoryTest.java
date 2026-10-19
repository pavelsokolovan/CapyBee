package com.capybee.engine.session.adapter;

import com.capybee.engine.api.PackRef;
import com.capybee.engine.api.RunSnapshot;
import com.capybee.engine.api.RunState;
import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.PackId;
import com.capybee.engine.pack.PackVersion;
import com.capybee.engine.session.SessionTypes;
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

import static com.capybee.engine.session.SessionPorts.CreateKind;
import static com.capybee.engine.session.SessionTypes.*;
import static org.assertj.core.api.Assertions.assertThat;

class FileRunRepositoryTest {
    @TempDir
    Path directory;

    @Test
    void persistsSnapshotAndLeaseStateAcrossInstances() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-19T10:00:00Z"), ZoneOffset.UTC);
        FileRunRepository first = new FileRunRepository(directory, clock);
        OwnerRef owner = new OwnerRef("file-owner");
        UUID runId = UUID.randomUUID();
        var packId = new PackId("test-pack");
        var locale = new LocaleTag("en");
        Instant now = clock.instant();
        NewRun newRun = new NewRun(runId, owner, packId, "1.0.0", locale, Map.of(), 42L,
                false, now);
        assertThat(first.createPreparing(newRun).kind()).isEqualTo(CreateKind.CREATED);
        Lease lease = first.tryClaim(runId, owner, Duration.ofSeconds(10)).orElseThrow();
        RunSnapshot snapshot = new RunSnapshot(1, runId.toString(), new PackRef(packId,
                new PackVersion(1, 0, 0)), locale, 42, Map.of(), now, now, 0,
                new RunState("start", List.of("start"), false, null));

        assertThat(first.saveActive(runId, lease, 0, snapshot, List.of())).isTrue();
        StoredRun restored = new FileRunRepository(directory, clock).find(runId, owner).orElseThrow();
        assertThat(restored.status()).isEqualTo(RunStatus.ACTIVE);
        assertThat(restored.revision()).isZero();
        assertThat(restored.snapshot()).isEqualTo(snapshot);
        assertThat(restored.pendingStart()).isNull();
        assertThat(first.tryClaim(runId, new OwnerRef("different-owner"), Duration.ofSeconds(5))).isEmpty();
    }

    @Test
    void refusesSecondOpenRunAndReleasesOnlyItsLease() {
        FileRunRepository repository = new FileRunRepository(directory);
        OwnerRef owner = new OwnerRef("file-owner");
        UUID runId = UUID.randomUUID();
        var packId = new PackId("test-pack");
        var locale = new LocaleTag("en");
        Instant now = Instant.now();
        repository.createPreparing(new NewRun(runId, owner, packId, "1.0.0", locale, Map.of(),
                null, false, now));
        assertThat(repository.createPreparing(new NewRun(UUID.randomUUID(), owner, packId, "1.0.0",
                locale, Map.of(), null, false, now)).kind()).isEqualTo(CreateKind.OWNER_HAS_ACTIVE);

        Lease lease = repository.tryClaim(runId, owner, Duration.ofSeconds(5)).orElseThrow();
        repository.releaseLease(runId, new Lease(UUID.randomUUID(), lease.until()));
        assertThat(repository.tryClaim(runId, owner, Duration.ofSeconds(5))).isEmpty();
        repository.releaseLease(runId, lease);
        assertThat(repository.tryClaim(runId, owner, Duration.ofSeconds(5))).isPresent();
    }
}
