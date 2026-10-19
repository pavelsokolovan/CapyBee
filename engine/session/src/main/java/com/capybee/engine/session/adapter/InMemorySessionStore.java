package com.capybee.engine.session.adapter;

import com.capybee.engine.api.Event;
import com.capybee.engine.api.RunSnapshot;
import com.capybee.engine.generation.GenerationArtifact;
import com.capybee.engine.port.TextGenerationResult;
import com.capybee.engine.session.SessionPorts;
import com.capybee.engine.session.SessionTypes;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.capybee.engine.session.SessionPorts.CreateKind;
import static com.capybee.engine.session.SessionPorts.CreateOutcome;
import static com.capybee.engine.session.SessionPorts.InsertKind;
import static com.capybee.engine.session.SessionTypes.*;

public final class InMemorySessionStore implements SessionPorts.RunRepository, SessionPorts.CommandLog,
        SessionPorts.GenerationLedger, SessionPorts.EventOutbox, SessionPorts.PrefetchStore,
        SessionPorts.Transaction {
    private final Clock clock;
    private final Map<UUID, StoredRun> runs = new HashMap<>();
    private final Map<String, LoggedCommand> commands = new HashMap<>();
    private final Map<UUID, List<OutboxEvent>> events = new HashMap<>();
    private final Map<UUID, Map<String, PrefetchedArtifact>> prefetch = new HashMap<>();
    private final Map<String, Integer> reservations = new HashMap<>();
    private final Map<String, Integer> runReservations = new HashMap<>();
    private final Map<String, long[]> usage = new HashMap<>();

    public InMemorySessionStore() {
        this(Clock.systemUTC());
    }

    public InMemorySessionStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public synchronized CreateOutcome createPreparing(NewRun newRun) {
        StoredRun existing = runs.get(newRun.id());
        if (existing != null) {
            return new CreateOutcome(existing.owner().equals(newRun.owner())
                    ? CreateKind.EXISTS_SAME_OWNER : CreateKind.EXISTS_OTHER_OWNER, existing.id());
        }
        Optional<StoredRun> active = runs.values().stream()
                .filter(run -> run.owner().equals(newRun.owner()))
                .filter(run -> run.status() == RunStatus.PREPARING || run.status() == RunStatus.ACTIVE)
                .findFirst();
        if (active.isPresent()) {
            return new CreateOutcome(CreateKind.OWNER_HAS_ACTIVE, active.get().id());
        }
        StartRequest pending = new StartRequest(newRun.id(), newRun.packId(), newRun.locale(),
                newRun.startInputs(), newRun.seed(), newRun.generated());
        runs.put(newRun.id(), new StoredRun(newRun.id(), newRun.owner(), newRun.packId(), newRun.locale(),
                RunStatus.PREPARING, 0, null, pending, null, null, 0, newRun.createdAt(), newRun.createdAt()));
        return new CreateOutcome(CreateKind.CREATED, newRun.id());
    }

    @Override
    public synchronized Optional<StoredRun> find(UUID runId, OwnerRef owner) {
        StoredRun run = runs.get(runId);
        return run != null && run.owner().equals(owner) ? Optional.of(run) : Optional.empty();
    }

    @Override
    public synchronized Optional<StoredRun> findCurrent(OwnerRef owner) {
        return runs.values().stream()
                .filter(run -> run.owner().equals(owner))
                .filter(run -> run.status() == RunStatus.PREPARING || run.status() == RunStatus.ACTIVE)
                .max(Comparator.comparing(StoredRun::updatedAt));
    }

    @Override
    public synchronized Optional<Lease> tryClaim(UUID runId, OwnerRef owner, Duration ttl) {
        StoredRun run = runs.get(runId);
        Instant now = clock.instant();
        if (run == null || !run.owner().equals(owner)
                || (run.lease() != null && run.lease().until().isAfter(now))) {
            return Optional.empty();
        }
        Lease lease = new Lease(UUID.randomUUID(), now.plus(ttl));
        runs.put(runId, copy(run, run.status(), run.revision(), run.snapshot(), run.pendingStart(),
                lease, run.failureCode(), run.generationCalls(), now));
        return Optional.of(lease);
    }

    @Override
    public synchronized boolean saveActive(UUID runId, Lease lease, long expectedRevision,
                                           RunSnapshot snapshot, List<Event> ignoredEvents) {
        StoredRun run = runs.get(runId);
        if (!hasLease(run, lease) || run.revision() != expectedRevision) return false;
        RunStatus status = snapshot.state().ended() ? RunStatus.ENDED : RunStatus.ACTIVE;
        long savedRevision = run.status() == RunStatus.PREPARING ? expectedRevision : expectedRevision + 1;
        runs.put(runId, copy(run, status, savedRevision, snapshot, null, null,
                null, run.generationCalls(), clock.instant()));
        return true;
    }

    @Override
    public synchronized boolean markFailed(UUID runId, Lease lease, String failureCode) {
        StoredRun run = runs.get(runId);
        if (!hasLease(run, lease)) return false;
        runs.put(runId, copy(run, RunStatus.FAILED, run.revision(), null, null, null,
                failureCode, run.generationCalls(), clock.instant()));
        return true;
    }

    @Override
    public synchronized void releaseLease(UUID runId, Lease lease) {
        StoredRun run = runs.get(runId);
        if (hasLease(run, lease)) {
            runs.put(runId, copy(run, run.status(), run.revision(), run.snapshot(),
                    run.pendingStart(), null, run.failureCode(), run.generationCalls(), clock.instant()));
        }
    }

    @Override
    public synchronized void delete(UUID runId, OwnerRef owner) {
        StoredRun run = runs.get(runId);
        if (run != null && run.owner().equals(owner)) {
            runs.remove(runId);
            commands.keySet().removeIf(key -> key.startsWith(runId + ":"));
            events.remove(runId);
            prefetch.remove(runId);
        }
    }

    @Override
    public synchronized Optional<LoggedCommand> find(UUID runId, UUID commandId) {
        return Optional.ofNullable(commands.get(commandKey(runId, commandId)));
    }

    @Override
    public synchronized InsertKind begin(UUID runId, UUID commandId, byte[] payloadMac, long revisionBefore) {
        String key = commandKey(runId, commandId);
        if (commands.containsKey(key)) return InsertKind.EXISTS;
        commands.put(key, new LoggedCommand(payloadMac, CommandState.IN_PROGRESS, null,
                revisionBefore, null, List.of(), null));
        return InsertKind.INSERTED;
    }

    @Override
    public synchronized void complete(UUID runId, UUID commandId, boolean accepted,
                                      long revisionAfter, List<Event> commandEvents) {
        String key = commandKey(runId, commandId);
        LoggedCommand prior = commands.get(key);
        if (prior == null) throw new IllegalStateException("COMMAND_NOT_BEGUN");
        commands.put(key, new LoggedCommand(prior.payloadMac(), CommandState.DONE, accepted,
                prior.revisionBefore(), revisionAfter, commandEvents, null));
    }

    @Override
    public synchronized void fail(UUID runId, UUID commandId, String code) {
        String key = commandKey(runId, commandId);
        LoggedCommand prior = commands.get(key);
        if (prior != null) {
            commands.put(key, new LoggedCommand(prior.payloadMac(), CommandState.FAILED, false,
                    prior.revisionBefore(), prior.revisionAfter(), prior.events(), code));
        }
    }

    @Override
    public synchronized boolean tryReserveCall(LedgerScope profile, LedgerScope global, LocalDate day,
                                              UUID runId, int runLimit,
                                              com.capybee.engine.session.MeteredTextGenerator.Priority priority,
                                              int profileReserve, int globalReserve, int runReserve) {
        String profileKey = usageKey(profile.value(), day);
        String globalKey = usageKey(global.value(), day);
        String runKey = runId.toString();
        int profileCalls = reservations.getOrDefault(profileKey, 0);
        int globalCalls = reservations.getOrDefault(globalKey, 0);
        int runCalls = runReservations.getOrDefault(runKey, 0);
        if (priority == com.capybee.engine.session.MeteredTextGenerator.Priority.PREFETCH) {
            if (profileCalls >= Math.max(0, profile.dailyCallLimit() - profileReserve)
                    || globalCalls >= Math.max(0, global.dailyCallLimit() - globalReserve)
                    || runCalls >= Math.max(0, runLimit - runReserve)) return false;
        } else if (profileCalls >= profile.dailyCallLimit() || globalCalls >= global.dailyCallLimit()
                || runCalls >= runLimit) {
            return false;
        }
        reservations.put(profileKey, profileCalls + 1);
        reservations.put(globalKey, globalCalls + 1);
        runReservations.put(runKey, runCalls + 1);
        return true;
    }

    @Override
    public synchronized void recordUsage(LedgerScope profile, LedgerScope global, LocalDate day,
                                         UUID runId, TextGenerationResult.TokenUsage tokenUsage,
                                         com.capybee.engine.session.MeteredTextGenerator.Priority priority) {
        addUsage(usageKey(profile.value(), day), tokenUsage, priority);
        addUsage(usageKey(global.value(), day), tokenUsage, priority);
    }

    @Override
    public synchronized void releaseCall(LedgerScope profile, LedgerScope global, LocalDate day, UUID runId) {
        decrement(usageKey(profile.value(), day));
        decrement(usageKey(global.value(), day));
        runReservations.computeIfPresent(runId.toString(), (ignored, count) -> Math.max(0, count - 1));
    }

    @Override
    public synchronized void append(UUID runId, UUID commandId, long revision, List<Event> added) {
        List<OutboxEvent> runEvents = events.computeIfAbsent(runId, ignored -> new ArrayList<>());
        Instant now = clock.instant();
        for (Event event : added) {
            runEvents.add(new OutboxEvent(runId, runEvents.size() + 1L, commandId, revision, event, now));
        }
    }

    @Override
    public synchronized List<OutboxEvent> readAfter(UUID runId, long afterSeq, int limit) {
        if (limit < 1) throw new IllegalArgumentException("limit must be positive");
        return events.getOrDefault(runId, List.of()).stream()
                .filter(event -> event.seq() > afterSeq).limit(limit).toList();
    }

    @Override
    public synchronized List<PrefetchedArtifact> load(UUID runId) {
        return List.copyOf(prefetch.getOrDefault(runId, Map.of()).values());
    }

    @Override
    public synchronized boolean insertIfAbsent(UUID runId, String sceneId, long basedOnRevision,
                                               GenerationArtifact artifact) {
        if (artifact.outcome() == GenerationArtifact.Outcome.FALLBACK) return false;
        return prefetch.computeIfAbsent(runId, ignored -> new HashMap<>())
                .putIfAbsent(sceneId, new PrefetchedArtifact(sceneId, basedOnRevision, artifact)) == null;
    }

    @Override
    public synchronized void deleteForScenes(UUID runId, Collection<String> sceneIds) {
        Map<String, PrefetchedArtifact> artifacts = prefetch.get(runId);
        if (artifacts != null) sceneIds.forEach(artifacts::remove);
    }

    @Override
    public synchronized void execute(Runnable work) {
        work.run();
    }

    private boolean hasLease(StoredRun run, Lease lease) {
        return run != null && run.lease() != null && lease != null
                && run.lease().token().equals(lease.token());
    }

    private StoredRun copy(StoredRun old, RunStatus status, long revision, RunSnapshot snapshot,
                           StartRequest pendingStart, Lease lease, String failureCode, int generationCalls,
                           Instant updatedAt) {
        return new StoredRun(old.id(), old.owner(), old.packId(), old.locale(), status, revision,
                snapshot, pendingStart, lease, failureCode, generationCalls, old.createdAt(), updatedAt);
    }

    private void addUsage(String key, TextGenerationResult.TokenUsage value,
                          com.capybee.engine.session.MeteredTextGenerator.Priority priority) {
        long[] totals = usage.computeIfAbsent(key, ignored -> new long[3]);
        totals[0] += value.inputTokens();
        totals[1] += value.outputTokens();
        if (priority == com.capybee.engine.session.MeteredTextGenerator.Priority.PREFETCH) totals[2]++;
    }

    private void decrement(String key) {
        reservations.computeIfPresent(key, (ignored, count) -> Math.max(0, count - 1));
    }

    private static String commandKey(UUID runId, UUID commandId) {
        return runId + ":" + commandId;
    }

    private static String usageKey(String scope, LocalDate day) {
        return scope + ":" + day;
    }
}
