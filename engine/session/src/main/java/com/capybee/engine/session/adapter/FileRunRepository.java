package com.capybee.engine.session.adapter;

import com.capybee.engine.api.Event;
import com.capybee.engine.api.RunSnapshot;
import com.capybee.engine.api.json.SnapshotCodec;
import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.PackId;
import com.capybee.engine.session.SessionPorts;
import com.capybee.engine.session.SessionTypes;
import com.capybee.engine.session.wire.SessionJson;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static com.capybee.engine.session.SessionPorts.CreateKind;
import static com.capybee.engine.session.SessionPorts.CreateOutcome;
import static com.capybee.engine.session.SessionTypes.*;

public final class FileRunRepository implements SessionPorts.RunRepository {
    private final Path directory;
    private final Clock clock;

    public FileRunRepository(Path directory) {
        this(directory, Clock.systemUTC());
    }

    public FileRunRepository(Path directory, Clock clock) {
        this.directory = directory.toAbsolutePath().normalize();
        this.clock = clock;
    }

    @Override
    public synchronized CreateOutcome createPreparing(NewRun run) {
        Optional<FileRun> existing = read(run.id());
        if (existing.isPresent()) {
            return new CreateOutcome(existing.get().owner().equals(run.owner().value())
                    ? CreateKind.EXISTS_SAME_OWNER : CreateKind.EXISTS_OTHER_OWNER, run.id());
        }
        Optional<FileRun> current = allRuns().stream()
                .filter(item -> item.owner().equals(run.owner().value()))
                .filter(item -> item.status() == RunStatus.PREPARING || item.status() == RunStatus.ACTIVE)
                .findFirst();
        if (current.isPresent()) {
            return new CreateOutcome(CreateKind.OWNER_HAS_ACTIVE, current.get().id());
        }
        Instant now = run.createdAt();
        StartRequest start = new StartRequest(run.id(), run.packId(), run.locale(), run.startInputs(),
                run.seed(), run.generated());
        write(new FileRun(run.id(), run.owner().value(), run.packId().value(), run.packVersion(),
                run.locale().value(), RunStatus.PREPARING, 0, null, encodeStart(start),
                null, null, null, 0, now, now));
        return new CreateOutcome(CreateKind.CREATED, run.id());
    }

    @Override
    public synchronized Optional<StoredRun> find(UUID runId, OwnerRef owner) {
        return read(runId).filter(run -> run.owner().equals(owner.value())).map(this::toStored);
    }

    @Override
    public synchronized Optional<StoredRun> findCurrent(OwnerRef owner) {
        return allRuns().stream()
                .filter(run -> run.owner().equals(owner.value()))
                .filter(run -> run.status() == RunStatus.PREPARING || run.status() == RunStatus.ACTIVE)
                .max(java.util.Comparator.comparing(FileRun::updatedAt))
                .map(this::toStored);
    }

    @Override
    public synchronized Optional<Lease> tryClaim(UUID runId, OwnerRef owner, Duration ttl) {
        FileRun run = read(runId).filter(item -> item.owner().equals(owner.value())).orElse(null);
        Instant now = clock.instant();
        if (run == null || (run.leaseUntil() != null && run.leaseUntil().isAfter(now))
                || (run.status() != RunStatus.PREPARING && run.status() != RunStatus.ACTIVE)) {
            return Optional.empty();
        }
        Lease lease = new Lease(UUID.randomUUID(), now.plus(ttl));
        write(copy(run, run.status(), run.revision(), run.snapshotJson(), run.pendingStartJson(),
                lease.token(), lease.until(), run.failureCode(), run.generationCalls(), now));
        return Optional.of(lease);
    }

    @Override
    public synchronized boolean saveActive(UUID runId, Lease lease, long expectedRevision,
                                           RunSnapshot snapshot, List<Event> ignoredEvents) {
        FileRun run = read(runId).orElse(null);
        if (!hasLease(run, lease) || run.revision() != expectedRevision) return false;
        RunStatus status = snapshot.state().ended() ? RunStatus.ENDED : RunStatus.ACTIVE;
        long revision = run.status() == RunStatus.PREPARING ? expectedRevision : expectedRevision + 1;
        write(copy(run, status, revision, SnapshotCodec.toJson(snapshot), null, null,
                null, null, run.generationCalls(), clock.instant()));
        return true;
    }

    @Override
    public synchronized boolean markFailed(UUID runId, Lease lease, String failureCode) {
        FileRun run = read(runId).orElse(null);
        if (!hasLease(run, lease)) return false;
        write(copy(run, RunStatus.FAILED, run.revision(), null, null, null, null,
                failureCode, run.generationCalls(), clock.instant()));
        return true;
    }

    @Override
    public synchronized void releaseLease(UUID runId, Lease lease) {
        FileRun run = read(runId).orElse(null);
        if (hasLease(run, lease)) {
            write(copy(run, run.status(), run.revision(), run.snapshotJson(), run.pendingStartJson(),
                    null, null, run.failureCode(), run.generationCalls(), clock.instant()));
        }
    }

    @Override
    public synchronized void delete(UUID runId, OwnerRef owner) {
        if (find(runId, owner).isEmpty()) return;
        try {
            Files.deleteIfExists(file(runId));
        } catch (IOException exception) {
            throw new IllegalStateException("RUN_FILE_DELETE_FAILED");
        }
    }

    private StoredRun toStored(FileRun run) {
        RunSnapshot snapshot = null;
        if (run.snapshotJson() != null) {
            var parsed = SnapshotCodec.fromJson(run.snapshotJson());
            if (parsed.snapshot() == null) throw new IllegalStateException("RUN_SNAPSHOT_INVALID");
            snapshot = parsed.snapshot();
        }
        StartRequest pending = run.pendingStartJson() == null ? null : decodeStart(run.pendingStartJson());
        Lease lease = run.leaseToken() == null ? null : new Lease(run.leaseToken(), run.leaseUntil());
        return new StoredRun(run.id(), new OwnerRef(run.owner()), new PackId(run.packId()),
                new LocaleTag(run.locale()), run.status(), run.revision(), snapshot, pending, lease,
                run.failureCode(), run.generationCalls(), run.createdAt(), run.updatedAt());
    }

    private Optional<FileRun> read(UUID id) {
        Path path = file(id);
        if (!Files.exists(path)) return Optional.empty();
        try {
            return Optional.of(SessionJson.decode(Files.readString(path), FileRun.class));
        } catch (IOException exception) {
            throw new IllegalStateException("RUN_FILE_READ_FAILED");
        }
    }

    private List<FileRun> allRuns() {
        try {
            Files.createDirectories(directory);
            try (Stream<Path> files = Files.list(directory)) {
                return files.filter(path -> path.getFileName().toString().matches(
                                "[0-9a-fA-F-]{36}\\.json"))
                        .map(path -> SessionJson.decode(readFile(path), FileRun.class)).toList();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("RUN_FILE_LIST_FAILED");
        }
    }

    private String readFile(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("RUN_FILE_READ_FAILED");
        }
    }

    private void write(FileRun run) {
        try {
            Files.createDirectories(directory);
            Path target = file(run.id());
            Path temporary = Files.createTempFile(directory, run.id().toString(), ".tmp");
            try {
                Files.writeString(temporary, SessionJson.encode(run));
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException unsupported) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("RUN_FILE_WRITE_FAILED");
        }
    }

    private Path file(UUID id) {
        return directory.resolve(id.toString() + ".json");
    }

    private String encodeStart(StartRequest start) {
        return SessionJson.encode(new StartPayload(start.id(), start.packId().value(), start.locale().value(),
                start.startInputs(), start.seed(), start.generated()));
    }

    private StartRequest decodeStart(String json) {
        StartPayload payload = SessionJson.decode(json, StartPayload.class);
        return new StartRequest(payload.id(), new PackId(payload.packId()),
                new LocaleTag(payload.locale()), payload.startInputs(), payload.seed(), payload.generated());
    }

    private boolean hasLease(FileRun run, Lease lease) {
        return run != null && run.leaseToken() != null && lease != null
                && run.leaseToken().equals(lease.token());
    }

    private FileRun copy(FileRun old, RunStatus status, long revision, String snapshotJson,
                         String pendingStartJson, UUID leaseToken, Instant leaseUntil,
                         String failureCode, int generationCalls, Instant updatedAt) {
        return new FileRun(old.id(), old.owner(), old.packId(), old.packVersion(), old.locale(),
                status, revision, snapshotJson, pendingStartJson, leaseToken, leaseUntil,
                failureCode, generationCalls, old.createdAt(), updatedAt);
    }

    public record FileRun(UUID id, String owner, String packId, String packVersion, String locale,
                         RunStatus status, long revision, String snapshotJson, String pendingStartJson,
                         UUID leaseToken, Instant leaseUntil, String failureCode, int generationCalls,
                         Instant createdAt, Instant updatedAt) {}

    public record StartPayload(UUID id, String packId, String locale, Map<String, String> startInputs,
                               Long seed, boolean generated) {}
}
