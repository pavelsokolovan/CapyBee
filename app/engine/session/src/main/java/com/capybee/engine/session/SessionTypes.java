package com.capybee.engine.session;

import com.capybee.engine.api.Command;
import com.capybee.engine.api.Event;
import com.capybee.engine.api.Issue;
import com.capybee.engine.api.RunSnapshot;
import com.capybee.engine.api.View;
import com.capybee.engine.generation.GenerationArtifact;
import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.PackId;
import com.capybee.engine.port.TextGenerationResult;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SessionTypes {
    private SessionTypes() {}

    public enum RunStatus { PREPARING, ACTIVE, SUSPENDED, ENDED, FAILED, CANCELLING, CANCELLED }
    public enum CommandState { IN_PROGRESS, DONE, FAILED }
    public enum Outcome { APPLIED, REJECTED, REPLAYED, PROCESSING }
    public enum SceneSource { NONE, PREFETCHED, JOINED_PREFETCH, INLINE_GENERATED, INLINE_REPAIRED, FALLBACK }
    public enum ErrorCode {
        REQUEST_INVALID, GAME_DISABLED, PAYLOAD_TOO_LARGE, INTERNAL_ERROR, SERVICE_UNAVAILABLE,
        RATE_LIMITED, RUN_NOT_FOUND, RUN_ALREADY_ACTIVE, RUN_BUSY, RUN_NOT_ACTIVE,
        REVISION_CONFLICT, COMMAND_ID_REUSED, START_REJECTED, START_TIMEOUT,
        RUN_UNRECOVERABLE, GENERATION_BUSY, GENERATION_UNAVAILABLE, RUN_SUSPENDED,
        RUN_ARCHIVED, RUN_TRASHED, RUN_QUARANTINED, RUN_NOT_RESUMABLE, RUN_SWITCH_BUSY,
        COMMAND_INTERRUPTED, PACK_VERSION_MISMATCH, RECOVERY_IN_PROGRESS,
        PURGE_CONFIRMATION_REQUIRED
    }

    public record OwnerRef(String value) {
        public OwnerRef {
            if (value == null || value.isBlank()) throw new IllegalArgumentException("owner required");
        }
    }

    public record StartRequest(UUID id, PackId packId, LocaleTag locale,
                               Map<String, String> startInputs, Long seed, boolean generated) {
        public StartRequest {
            startInputs = startInputs == null ? Map.of() : Map.copyOf(startInputs);
        }
    }

    public record NewRun(UUID id, OwnerRef owner, PackId packId, String packVersion, LocaleTag locale,
                         Map<String, String> startInputs, Long seed, boolean generated,
                         Instant createdAt) {
        public NewRun {
            startInputs = Map.copyOf(startInputs);
        }
    }

    public enum LeaseKind { START, COMMAND, RECOVERY }
    public record Lease(UUID token, Instant until, UUID attemptId, Instant acquiredAt, LeaseKind kind) {
        public Lease(UUID token, Instant until) { this(token, until, token, null, null); }
    }

    public record StoredRun(UUID id, OwnerRef owner, PackId packId, LocaleTag locale,
                            RunStatus status, long revision, RunSnapshot snapshot,
                            StartRequest pendingStart, Lease lease, String failureCode,
                            int generationCalls, Instant createdAt, Instant updatedAt,
                            String packVersion, Instant lastPlayedAt, Instant archivedAt,
                            Instant trashedAt) {
        public StoredRun(UUID id, OwnerRef owner, PackId packId, LocaleTag locale,
                         RunStatus status, long revision, RunSnapshot snapshot,
                         StartRequest pendingStart, Lease lease, String failureCode,
                         int generationCalls, Instant createdAt, Instant updatedAt) {
            this(id, owner, packId, locale, status, revision, snapshot, pendingStart, lease,
                    failureCode, generationCalls, createdAt, updatedAt, null, updatedAt, null, null);
        }
    }

    public enum RecoverySource { EMPTY, PRIMARY, TEMP, BACKUP, LEGACY }
    public record RecoveryNotice(String code, UUID runId) {}
    public record QuarantinedRunSummary(UUID runId, String packId, String reasonCode,
                                        String hash, long size, Instant quarantinedAt) {}
    public record StoreRecoveryReport(UUID storeId, long selectedRevision, RecoverySource source,
                                      List<RecoveryNotice> notices,
                                      List<QuarantinedRunSummary> quarantinedRuns,
                                      boolean degraded) {
        public StoreRecoveryReport {
            notices = List.copyOf(notices == null ? List.of() : notices);
            quarantinedRuns = List.copyOf(quarantinedRuns == null ? List.of() : quarantinedRuns);
        }
    }
    public enum RecoveryState { READY, RECOVERED, WAITING_FOR_LEASE, RETRYING_START,
        INTERRUPTED_COMMAND, PACK_UNAVAILABLE, PACK_VERSION_MISMATCH, QUARANTINED, FAILED, NO_ACTION }
    public record RecoverableRun(UUID runId, long revision, RecoveryState state, String code) {}

    public record RunQuery(boolean includeArchived, boolean includeTrashed) {
        public static RunQuery visible() { return new RunQuery(false, false); }
    }
    public record PageToken(String value) {}
    public record RunDescriptor(UUID runId, PackId packId, String packVersion, LocaleTag locale,
                                RunStatus status, long revision, String title, Instant createdAt,
                                Instant lastPlayedAt, Instant endedAt, Instant archivedAt,
                                Instant trashedAt, boolean resumable, String recoveryCode,
                                long storageEstimate) {}
    public record RunPage(List<RunDescriptor> runs, PageToken nextToken) {
        public RunPage { runs = List.copyOf(runs); }
    }
    public record LifecycleOutcome(UUID runId, RunStatus status, boolean changed) {}
    public record PurgeConfirmation(boolean confirmed, boolean force, Duration gracePeriod) {
        public static PurgeConfirmation denied() { return new PurgeConfirmation(false, false, Duration.ofDays(30)); }
    }

    public record RunSummary(UUID runId, RunStatus status, long revision, PackId packId,
                             String packVersion, LocaleTag locale, String endReason,
                             String failureCode, View view, Instant updatedAt) {}

    public record StartAccepted(UUID runId, RunStatus status, long revision, long retryAfterMs) {}

    public record CommandRequest(UUID id, long expectedRevision, Command command) {}

    public record CommandOutcome(UUID commandId, Outcome outcome, boolean accepted,
                                 long appliedRevision, long revision, RunStatus status,
                                 View view, List<Event> events, List<Issue> issues) {
        public CommandOutcome {
            events = List.copyOf(events);
            issues = List.copyOf(issues);
        }
    }

    public record CommandStatus(boolean processing, CommandOutcome outcome, long retryAfterMs) {}

    public record LoggedCommand(byte[] payloadMac, CommandState state, Boolean accepted,
                                long revisionBefore, Long revisionAfter, List<Event> events,
                                String failureCode, Long latencyMillis, SceneSource sceneSource) {
        public LoggedCommand(byte[] payloadMac, CommandState state, Boolean accepted,
                             long revisionBefore, Long revisionAfter, List<Event> events,
                             String failureCode) {
            this(payloadMac, state, accepted, revisionBefore, revisionAfter, events,
                    failureCode, null, null);
        }
        public LoggedCommand {
            payloadMac = payloadMac.clone();
            events = List.copyOf(events);
        }
        @Override public byte[] payloadMac() { return payloadMac.clone(); }
    }

    public record CommandSummary(UUID commandId, CommandState state, Boolean accepted,
                                 long revisionBefore, Long revisionAfter, Long latencyMillis,
                                 SceneSource sceneSource) {}

    public record OutboxEvent(UUID runId, long seq, UUID commandId, long revision,
                              Event event, Instant createdAt) {}

    public record LedgerScope(String value, int dailyCallLimit) {
        public LedgerScope {
            if (value == null || value.isBlank() || dailyCallLimit < 0) {
                throw new IllegalArgumentException("invalid ledger scope");
            }
        }
    }

    public record Diagnostics(UUID runId, long revision, int generationCalls, int runCallCeiling,
                              PrefetchDiagnostics prefetch, List<CommandDiagnostics> recentCommands,
                              List<StageDiagnostics> stages, BudgetDiagnostics budget) {
        public Diagnostics {
            recentCommands = List.copyOf(recentCommands);
            stages = List.copyOf(stages);
        }
    }

    public record PrefetchDiagnostics(List<String> ready, List<String> inFlight, int prefetchCalls,
                                      int hits, int misses, int wastedCalls) {
        public PrefetchDiagnostics {
            ready = List.copyOf(ready);
            inFlight = List.copyOf(inFlight);
        }
    }

    public record CommandDiagnostics(long revisionAfter, long latencyMillis, SceneSource sceneSource) {}

    public record StageDiagnostics(String stage, String outcome, int providerCalls, long elapsedMillis,
                                   String failureCode, int inputTokens, int outputTokens) {}

    public record BudgetDiagnostics(int profileCallsLeftToday, int globalCallsLeftToday) {}

    public record DayUsage(int reservedCalls, long inputTokens, long outputTokens, int prefetchCalls) {}

    public record PrefetchedArtifact(String sceneId, long basedOnRevision,
                                     GenerationArtifact artifact) {}

    public record Usage(TextGenerationResult.TokenUsage tokens) {}
}
