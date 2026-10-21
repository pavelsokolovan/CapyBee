package com.capybee.engine.session;

import com.capybee.engine.api.Engine;
import com.capybee.engine.api.Event;
import com.capybee.engine.api.RunSnapshot;
import com.capybee.engine.generation.GenerationArtifact;
import com.capybee.engine.pack.Pack;
import com.capybee.engine.port.TextGenerationResult;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.capybee.engine.session.SessionTypes.*;

public final class SessionPorts {
    private SessionPorts() {}

    public enum CreateKind { CREATED, EXISTS_SAME_OWNER, EXISTS_OTHER_OWNER, OWNER_HAS_ACTIVE }
    public record CreateOutcome(CreateKind kind, UUID existingRunId) {}
    public enum InsertKind { INSERTED, EXISTS }

    public interface RunRepository {
        CreateOutcome createPreparing(NewRun run);
        Optional<StoredRun> find(UUID runId, OwnerRef owner);
        Optional<StoredRun> findCurrent(OwnerRef owner);
        Optional<Lease> tryClaim(UUID runId, OwnerRef owner, Duration ttl);
        boolean saveActive(UUID runId, Lease lease, long expectedRevision, RunSnapshot snapshot,
                           List<Event> events);
        boolean markFailed(UUID runId, Lease lease, String failureCode);
        void releaseLease(UUID runId, Lease lease);
        void delete(UUID runId, OwnerRef owner);
    }

    public interface CommandLog {
        Optional<LoggedCommand> find(UUID runId, UUID commandId);
        InsertKind begin(UUID runId, UUID commandId, byte[] payloadMac, long revisionBefore);
        default void complete(UUID runId, UUID commandId, boolean accepted, long revisionAfter,
                              List<Event> events) {
            complete(runId, commandId, accepted, revisionAfter, events, null, null);
        }
        void complete(UUID runId, UUID commandId, boolean accepted, long revisionAfter,
                      List<Event> events, Long latencyMillis, SceneSource sceneSource);
        void fail(UUID runId, UUID commandId, String code);
        List<CommandSummary> list(UUID runId);
    }

    public interface GenerationLedger {
        boolean tryReserveCall(LedgerScope profile, LedgerScope global, LocalDate day, UUID runId,
                               int runLimit, MeteredTextGenerator.Priority priority,
                               int profileReserve, int globalReserve, int runReserve);
        void recordUsage(LedgerScope profile, LedgerScope global, LocalDate day,
                         UUID runId, TextGenerationResult.TokenUsage usage,
                         MeteredTextGenerator.Priority priority);
        void releaseCall(LedgerScope profile, LedgerScope global, LocalDate day, UUID runId);
        DayUsage dayUsage(LedgerScope scope, LocalDate day);
        int runPrefetchCalls(UUID runId);
    }

    public interface EventOutbox {
        void append(UUID runId, UUID commandId, long revision, List<Event> events);
        List<OutboxEvent> readAfter(UUID runId, long afterSeq, int limit);
    }

    @FunctionalInterface
    public interface Transaction {
        void execute(Runnable work);
    }

    public interface PrefetchStore {
        List<PrefetchedArtifact> load(UUID runId);
        boolean insertIfAbsent(UUID runId, String sceneId, long basedOnRevision,
                               GenerationArtifact artifact);
        void deleteForScenes(UUID runId, Collection<String> sceneIds);
    }

    /** A complete, internally consistent store for one local session domain. */
    public interface SessionStore extends RunRepository, CommandLog, GenerationLedger,
            EventOutbox, PrefetchStore, Transaction {}

    @FunctionalInterface
    public interface EngineFactory {
        Engine create(OwnerRef owner, UUID runId, MeteredTextGenerator.Priority priority);
    }

    @FunctionalInterface
    public interface SessionClock {
        Instant now();
    }

    @FunctionalInterface
    public interface OwnerScopes {
        LedgerScope profileScope(OwnerRef owner);

        default LocalDate currentDay(Instant instant) {
            return LocalDate.ofInstant(instant, java.time.ZoneOffset.UTC);
        }
    }

    public interface PackCatalog {
        List<Pack> all();
        Optional<Pack> find(String packId);
    }
}
