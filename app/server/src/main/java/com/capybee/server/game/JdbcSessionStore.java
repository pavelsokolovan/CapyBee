package com.capybee.server.game;

import com.capybee.engine.api.Event;
import com.capybee.engine.api.PackRef;
import com.capybee.engine.api.RunSnapshot;
import com.capybee.engine.api.RunState;
import com.capybee.engine.api.json.SnapshotCodec;
import com.capybee.engine.generation.GenerationArtifact;
import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.PackId;
import com.capybee.engine.pack.PackVersion;
import com.capybee.engine.port.TextGenerationResult;
import com.capybee.engine.session.MeteredTextGenerator;
import com.capybee.engine.session.SessionPorts;
import com.capybee.engine.session.SessionTypes;
import com.capybee.engine.session.wire.SessionJson;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.capybee.engine.session.SessionPorts.CreateKind;
import static com.capybee.engine.session.SessionPorts.CreateOutcome;
import static com.capybee.engine.session.SessionPorts.InsertKind;
import static com.capybee.engine.session.SessionTypes.*;

@Repository
public class JdbcSessionStore implements SessionPorts.RunRepository, SessionPorts.CommandLog,
        SessionPorts.GenerationLedger, SessionPorts.EventOutbox, SessionPorts.PrefetchStore,
        SessionPorts.Transaction {
    private static final RowMapper<StoredRun> RUN_MAPPER = JdbcSessionStore::mapRun;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public JdbcSessionStore(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, Clock clock) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Override
    public CreateOutcome createPreparing(NewRun run) {
        try {
            jdbc.update("""
                    INSERT INTO game_runs (id, user_id, pack_id, pack_version, locale, status, revision,
                                           pending_start, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, 'PREPARING', 0, ?::jsonb, ?, ?)
                    """, run.id(), ownerId(run.owner()), run.packId().value(), run.packVersion(),
                    run.locale().value(), encodeStart(run), run.createdAt(), run.createdAt());
            return new CreateOutcome(CreateKind.CREATED, run.id());
        } catch (DuplicateKeyException duplicate) {
            Optional<UUID> sameIdOwner = jdbc.query("""
                    SELECT id FROM game_runs WHERE id = ? AND user_id = ?
                    """, (rs, row) -> rs.getObject(1, UUID.class), run.id(), ownerId(run.owner()))
                    .stream().findFirst();
            if (sameIdOwner.isPresent()) {
                return new CreateOutcome(CreateKind.EXISTS_SAME_OWNER, sameIdOwner.get());
            }
            Optional<UUID> sameId = jdbc.query("SELECT id FROM game_runs WHERE id = ?",
                    (rs, row) -> rs.getObject(1, UUID.class), run.id()).stream().findFirst();
            if (sameId.isPresent()) return new CreateOutcome(CreateKind.EXISTS_OTHER_OWNER, sameId.get());
            UUID active = jdbc.query("""
                    SELECT id FROM game_runs WHERE user_id = ? AND status IN ('PREPARING', 'ACTIVE')
                    ORDER BY updated_at DESC LIMIT 1
                    """, (rs, row) -> rs.getObject(1, UUID.class), ownerId(run.owner()))
                    .stream().findFirst().orElseThrow(() -> duplicate);
            return new CreateOutcome(CreateKind.OWNER_HAS_ACTIVE, active);
        }
    }

    @Override
    public Optional<StoredRun> find(UUID runId, OwnerRef owner) {
        return jdbc.query("SELECT * FROM game_runs WHERE id = ? AND user_id = ?",
                RUN_MAPPER, runId, ownerId(owner)).stream().findFirst();
    }

    @Override
    public Optional<StoredRun> findCurrent(OwnerRef owner) {
        return jdbc.query("""
                SELECT * FROM game_runs WHERE user_id = ? AND status IN ('PREPARING', 'ACTIVE')
                ORDER BY updated_at DESC LIMIT 1
                """, RUN_MAPPER, ownerId(owner)).stream().findFirst();
    }

    @Override
    public Optional<Lease> tryClaim(UUID runId, OwnerRef owner, Duration ttl) {
        UUID token = UUID.randomUUID();
        Instant until = clock.instant().plus(ttl);
        int changed = jdbc.update("""
                UPDATE game_runs SET lease_token = ?, lease_until = ?, updated_at = ?
                WHERE id = ? AND user_id = ? AND status IN ('PREPARING', 'ACTIVE')
                  AND (lease_until IS NULL OR lease_until <= ?)
                """, token, until, clock.instant(), runId, ownerId(owner), clock.instant());
        return changed == 1 ? Optional.of(new Lease(token, until)) : Optional.empty();
    }

    @Override
    public boolean saveActive(UUID runId, Lease lease, long expectedRevision,
                              RunSnapshot snapshot, List<Event> ignoredEvents) {
        Instant now = clock.instant();
        int changed = jdbc.update("""
                UPDATE game_runs SET
                    status = ?, end_reason = ?, revision = CASE WHEN status = 'PREPARING'
                        THEN revision ELSE revision + 1 END,
                    snapshot = ?::jsonb, pending_start = NULL, lease_token = NULL, lease_until = NULL,
                    updated_at = ?, ended_at = CASE WHEN ? = 'ENDED' THEN ? ELSE ended_at END
                WHERE id = ? AND lease_token = ? AND revision = ? AND status IN ('PREPARING', 'ACTIVE')
                """, snapshot.state().ended() ? "ENDED" : "ACTIVE",
                snapshot.state().endReason() == null ? null : snapshot.state().endReason().name(),
                SnapshotCodec.toJson(snapshot), now, snapshot.state().ended() ? "ENDED" : "ACTIVE",
                now, runId, lease.token(), expectedRevision);
        return changed == 1;
    }

    @Override
    public boolean markFailed(UUID runId, Lease lease, String failureCode) {
        return jdbc.update("""
                UPDATE game_runs SET status = 'FAILED', failure_code = ?, pending_start = NULL,
                    lease_token = NULL, lease_until = NULL, updated_at = ?
                WHERE id = ? AND lease_token = ?
                """, failureCode, clock.instant(), runId, lease.token()) == 1;
    }

    @Override
    public void releaseLease(UUID runId, Lease lease) {
        jdbc.update("""
                UPDATE game_runs SET lease_token = NULL, lease_until = NULL, updated_at = ?
                WHERE id = ? AND lease_token = ?
                """, clock.instant(), runId, lease.token());
    }

    @Override
    public void delete(UUID runId, OwnerRef owner) {
        jdbc.update("DELETE FROM game_runs WHERE id = ? AND user_id = ?", runId, ownerId(owner));
    }

    @Override
    public Optional<LoggedCommand> find(UUID runId, UUID commandId) {
        Optional<LoggedCommand> logged = jdbc.query("""
                SELECT payload_mac, state, accepted, revision_before, revision_after, failure_code
                FROM game_run_commands WHERE run_id = ? AND command_id = ?
                """, (rs, row) -> new LoggedCommand(rs.getBytes("payload_mac"),
                CommandState.valueOf(rs.getString("state")), (Boolean) rs.getObject("accepted"),
                rs.getLong("revision_before"), nullableLong(rs, "revision_after"), List.of(),
                rs.getString("failure_code")), runId, commandId).stream().findFirst();
        return logged.map(value -> new LoggedCommand(value.payloadMac(), value.state(), value.accepted(),
                value.revisionBefore(), value.revisionAfter(), eventsForCommand(runId, commandId),
                value.failureCode()));
    }

    @Override
    public InsertKind begin(UUID runId, UUID commandId, byte[] payloadMac, long revisionBefore) {
        try {
            jdbc.update("""
                    INSERT INTO game_run_commands
                        (run_id, command_id, payload_mac, state, revision_before, created_at)
                    VALUES (?, ?, ?, 'IN_PROGRESS', ?, ?)
                    """, runId, commandId, payloadMac, revisionBefore, clock.instant());
            return InsertKind.INSERTED;
        } catch (DuplicateKeyException duplicate) {
            return InsertKind.EXISTS;
        }
    }

    @Override
    public void complete(UUID runId, UUID commandId, boolean accepted, long revisionAfter,
                         List<Event> events) {
        jdbc.update("""
                UPDATE game_run_commands SET state = 'DONE', accepted = ?, revision_after = ?
                WHERE run_id = ? AND command_id = ?
                """, accepted, revisionAfter, runId, commandId);
    }

    @Override
    public void fail(UUID runId, UUID commandId, String code) {
        jdbc.update("""
                UPDATE game_run_commands SET state = 'FAILED', accepted = FALSE, failure_code = ?
                WHERE run_id = ? AND command_id = ?
                """, code, runId, commandId);
    }

    @Override
    public boolean tryReserveCall(LedgerScope profile, LedgerScope global, LocalDate day,
                                  UUID runId, int runLimit, MeteredTextGenerator.Priority priority,
                                  int profileReserve, int globalReserve, int runReserve) {
        try {
            return Boolean.TRUE.equals(transaction.execute(status -> {
                int effectiveProfileCap = priority == MeteredTextGenerator.Priority.PREFETCH
                        ? Math.max(0, profile.dailyCallLimit() - profileReserve) : profile.dailyCallLimit();
                int effectiveGlobalCap = priority == MeteredTextGenerator.Priority.PREFETCH
                        ? Math.max(0, global.dailyCallLimit() - globalReserve) : global.dailyCallLimit();
                int effectiveRunCap = priority == MeteredTextGenerator.Priority.PREFETCH
                        ? Math.max(0, runLimit - runReserve) : runLimit;
                if (!reserveRun(runId, effectiveRunCap)) throw new ReservationDenied();
                if (!reserveScope(profile.value(), effectiveProfileCap, day)) throw new ReservationDenied();
                if (!reserveScope(global.value(), effectiveGlobalCap, day)) throw new ReservationDenied();
                return true;
            }));
        } catch (ReservationDenied denied) {
            return false;
        }
    }

    @Override
    public void recordUsage(LedgerScope profile, LedgerScope global, LocalDate day, UUID runId,
                           TextGenerationResult.TokenUsage usage, MeteredTextGenerator.Priority priority) {
        jdbc.update("""
                UPDATE game_generation_usage SET input_tokens = input_tokens + ?,
                    output_tokens = output_tokens + ?, prefetch_calls = prefetch_calls + ?
                WHERE scope = ? AND day = ?
                """, usage.inputTokens(), usage.outputTokens(),
                priority == MeteredTextGenerator.Priority.PREFETCH ? 1 : 0, profile.value(), day);
        jdbc.update("""
                UPDATE game_generation_usage SET input_tokens = input_tokens + ?,
                    output_tokens = output_tokens + ?, prefetch_calls = prefetch_calls + ?
                WHERE scope = ? AND day = ?
                """, usage.inputTokens(), usage.outputTokens(),
                priority == MeteredTextGenerator.Priority.PREFETCH ? 1 : 0, global.value(), day);
    }

    @Override
    public void releaseCall(LedgerScope profile, LedgerScope global, LocalDate day, UUID runId) {
        jdbc.update("UPDATE game_generation_usage SET calls = GREATEST(0, calls - 1) WHERE scope = ? AND day = ?",
                profile.value(), day);
        jdbc.update("UPDATE game_generation_usage SET calls = GREATEST(0, calls - 1) WHERE scope = ? AND day = ?",
                global.value(), day);
        jdbc.update("UPDATE game_runs SET generation_calls = GREATEST(0, generation_calls - 1) WHERE id = ?",
                runId);
    }

    @Override
    public void append(UUID runId, UUID commandId, long revision, List<Event> events) {
        if (events.isEmpty()) return;
        Long nextSeq = jdbc.queryForObject("SELECT COALESCE(MAX(seq), 0) + 1 FROM game_run_events WHERE run_id = ?",
                Long.class, runId);
        long seq = nextSeq == null ? 1 : nextSeq;
        Instant now = clock.instant();
        for (Event event : events) {
            jdbc.update("""
                    INSERT INTO game_run_events (run_id, seq, command_id, revision, event_id, payload, created_at)
                    VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
                    """, runId, seq++, commandId, revision, event.id(),
                    SessionJson.encode(event.payload()), now);
        }
    }

    @Override
    public List<OutboxEvent> readAfter(UUID runId, long afterSeq, int limit) {
        return jdbc.query("""
                SELECT seq, command_id, revision, event_id, payload, created_at FROM game_run_events
                WHERE run_id = ? AND seq > ? ORDER BY seq LIMIT ?
                """, (rs, row) -> {
            @SuppressWarnings("unchecked")
            Map<String, String> payload = SessionJson.decode(rs.getString("payload"), Map.class);
            return new OutboxEvent(runId, rs.getLong("seq"), rs.getObject("command_id", UUID.class),
                    rs.getLong("revision"), new Event(rs.getString("event_id"), payload),
                    rs.getTimestamp("created_at").toInstant());
        }, runId, afterSeq, limit);
    }

    @Override
    public List<PrefetchedArtifact> load(UUID runId) {
        return jdbc.query("""
                SELECT scene_id, based_on_revision, artifact FROM game_run_prefetch WHERE run_id = ?
                """, (rs, row) -> new PrefetchedArtifact(rs.getString("scene_id"),
                rs.getLong("based_on_revision"), decodeArtifact(rs.getString("artifact"))), runId);
    }

    @Override
    public boolean insertIfAbsent(UUID runId, String sceneId, long basedOnRevision,
                                  GenerationArtifact artifact) {
        if (artifact.outcome() == GenerationArtifact.Outcome.FALLBACK) return false;
        try {
            jdbc.update("""
                    INSERT INTO game_run_prefetch (run_id, scene_id, based_on_revision, artifact, created_at)
                    VALUES (?, ?, ?, ?::jsonb, ?)
                    """, runId, sceneId, basedOnRevision, encodeArtifact(artifact), clock.instant());
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    @Override
    public void deleteForScenes(UUID runId, Collection<String> sceneIds) {
        for (String sceneId : sceneIds) {
            jdbc.update("DELETE FROM game_run_prefetch WHERE run_id = ? AND scene_id = ?", runId, sceneId);
        }
    }

    @Override
    public void execute(Runnable work) {
        transaction.executeWithoutResult(status -> work.run());
    }

    private boolean reserveRun(UUID runId, int cap) {
        if (cap < 1) return false;
        return !jdbc.query("""
                UPDATE game_runs SET generation_calls = generation_calls + 1
                WHERE id = ? AND generation_calls < ? RETURNING generation_calls
                """, (rs, row) -> rs.getInt(1), runId, cap).isEmpty();
    }

    private boolean reserveScope(String scope, int cap, LocalDate day) {
        if (cap < 1) return false;
        return !jdbc.query("""
                INSERT INTO game_generation_usage (scope, day, calls, prefetch_calls, input_tokens, output_tokens)
                VALUES (?, ?, 1, 0, 0, 0)
                ON CONFLICT (scope, day) DO UPDATE SET calls = game_generation_usage.calls + 1
                WHERE game_generation_usage.calls < ?
                RETURNING calls
                """, (rs, row) -> rs.getInt(1), scope, day, cap).isEmpty();
    }

    private String encodeArtifact(GenerationArtifact artifact) {
        Instant now = clock.instant();
        RunSnapshot wrapper = new RunSnapshot(1, "prefetch-artifact",
                new PackRef(artifact.packId(), artifact.packVersion()), artifact.locale(), 0,
                Map.of(), now, now, 0, new RunState("prefetch", List.of(), false, null,
                Map.of(), Map.of(), Map.of(), Map.of(), null, null, List.of(), List.of(artifact)));
        return SnapshotCodec.toJson(wrapper);
    }

    private GenerationArtifact decodeArtifact(String json) {
        var parsed = SnapshotCodec.fromJson(json);
        if (parsed.snapshot() == null || parsed.snapshot().state().generationArtifacts().size() != 1) {
            throw new IllegalStateException("PREFETCH_ARTIFACT_INVALID");
        }
        return parsed.snapshot().state().generationArtifacts().getFirst();
    }

    private String encodeStart(NewRun run) {
        return SessionJson.encode(new StartPayload(run.id(), run.packId().value(), run.locale().value(),
                run.startInputs(), run.seed(), run.generated()));
    }

    private static StoredRun mapRun(ResultSet rs, int row) throws SQLException {
        String snapshotJson = rs.getString("snapshot");
        String pendingJson = rs.getString("pending_start");
        UUID leaseToken = rs.getObject("lease_token", UUID.class);
        var status = RunStatus.valueOf(rs.getString("status"));
        Lease lease = leaseToken == null ? null : new Lease(leaseToken, instant(rs, "lease_until"));
        return new StoredRun(rs.getObject("id", UUID.class),
                new OwnerRef(rs.getObject("user_id", UUID.class).toString()),
                new PackId(rs.getString("pack_id")), new LocaleTag(rs.getString("locale")), status,
                rs.getLong("revision"), snapshotJson == null ? null : decodeSnapshot(snapshotJson),
                pendingJson == null ? null : decodeStart(pendingJson), lease,
                rs.getString("failure_code"), rs.getInt("generation_calls"),
                instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private static RunSnapshot decodeSnapshot(String json) {
        var parsed = SnapshotCodec.fromJson(json);
        if (parsed.snapshot() == null) throw new IllegalStateException("RUN_SNAPSHOT_INVALID");
        return parsed.snapshot();
    }

    private static StartRequest decodeStart(String json) {
        StartPayload payload = SessionJson.decode(json, StartPayload.class);
        return new StartRequest(payload.id(), new PackId(payload.packId()),
                new LocaleTag(payload.locale()), payload.startInputs(), payload.seed(), payload.generated());
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static UUID ownerId(OwnerRef owner) {
        return UUID.fromString(owner.value());
    }

    private List<Event> eventsForCommand(UUID runId, UUID commandId) {
        return jdbc.query("""
                SELECT event_id, payload FROM game_run_events
                WHERE run_id = ? AND command_id = ? ORDER BY seq
                """, (rs, row) -> {
            @SuppressWarnings("unchecked")
            Map<String, String> payload = SessionJson.decode(rs.getString("payload"), Map.class);
            return new Event(rs.getString("event_id"), payload);
        }, runId, commandId);
    }

    public record StartPayload(UUID id, String packId, String locale, Map<String, String> startInputs,
                               Long seed, boolean generated) {}
    private static final class ReservationDenied extends RuntimeException {}
}
