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
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.capybee.engine.session.SessionPorts.CreateKind;
import static com.capybee.engine.session.SessionPorts.CreateOutcome;
import static com.capybee.engine.session.SessionPorts.InsertKind;
import static com.capybee.engine.session.SessionTypes.*;

public final class InMemorySessionStore implements SessionPorts.SessionStore {
    private final Clock clock;
    private final Map<UUID, StoredRun> runs = new HashMap<>();
    private final Map<String, LoggedCommand> commands = new LinkedHashMap<>();
    private final Map<UUID, List<OutboxEvent>> events = new HashMap<>();
    private final Map<UUID, Map<String, PrefetchedArtifact>> prefetch = new HashMap<>();
    private final Map<String, Integer> reservations = new HashMap<>();
    private final Map<String, Integer> runReservations = new HashMap<>();
    private final Map<String, Integer> runPrefetchCalls = new HashMap<>();
    private final Map<String, long[]> usage = new HashMap<>();
    private int transactionDepth;
    private Snapshot transactionSnapshot;
    private final UUID storeId = UUID.randomUUID();

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
                RunStatus.PREPARING, 0, null, pending, null, null, 0, newRun.createdAt(), newRun.createdAt(),
                newRun.packVersion(), newRun.createdAt(), null, null));
        return new CreateOutcome(CreateKind.CREATED, newRun.id());
    }

    @Override
    public synchronized Optional<StoredRun> find(UUID runId, OwnerRef owner) {
        StoredRun run = runs.get(runId);
        return run != null && run.owner().equals(owner) ? Optional.of(withGenerationCalls(run)) : Optional.empty();
    }

    @Override
    public synchronized Optional<StoredRun> findCurrent(OwnerRef owner) {
        return runs.values().stream()
                .filter(run -> run.owner().equals(owner))
                .filter(run -> run.status() == RunStatus.PREPARING || run.status() == RunStatus.ACTIVE)
                .max(Comparator.comparing(StoredRun::updatedAt)).map(this::withGenerationCalls);
    }

    @Override
    public synchronized Optional<Lease> tryClaim(UUID runId, OwnerRef owner, Duration ttl) {
        StoredRun run = runs.get(runId);
        Instant now = clock.instant();
        if (run == null || !run.owner().equals(owner)
                || (run.status() != RunStatus.PREPARING && run.status() != RunStatus.ACTIVE)
                || (run.lease() != null && run.lease().until().isAfter(now))) {
            return Optional.empty();
        }
        UUID token = UUID.randomUUID();
        Lease lease = new Lease(token, now.plus(ttl), token, now,
                run.status() == RunStatus.PREPARING ? LeaseKind.START : LeaseKind.COMMAND);
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
            runReservations.remove(runId.toString());
            runPrefetchCalls.remove(runId.toString());
        }
    }

    @Override
    public synchronized RunPage list(OwnerRef owner, RunQuery query, PageToken token, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be 1..100");
        RunQuery effective = query == null ? RunQuery.visible() : query;
        int offset = decodeToken(token, owner, effective);
        List<StoredRun> matching = runs.values().stream().filter(run -> run.owner().equals(owner))
                .filter(run -> effective.includeArchived() || run.archivedAt() == null)
                .filter(run -> effective.includeTrashed() || run.trashedAt() == null)
                .sorted(Comparator.comparing((StoredRun run) -> isCurrent(run) ? 0 : 1)
                        .thenComparing(StoredRun::lastPlayedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(run -> run.id().toString())).toList();
        int end = Math.min(matching.size(), offset + limit);
        List<RunDescriptor> page = matching.subList(Math.min(offset, matching.size()), end).stream()
                .map(this::descriptor).toList();
        PageToken next = end < matching.size() ? encodeToken(end, owner, effective) : null;
        return new RunPage(page, next);
    }

    @Override public synchronized LifecycleOutcome suspend(OwnerRef owner, UUID id) {
        StoredRun run = owned(owner, id); requireVisible(run);
        if (run.status() != RunStatus.ACTIVE || liveLease(run)) return unchanged(run);
        return updateLifecycle(run, RunStatus.SUSPENDED, run.archivedAt(), run.trashedAt());
    }
    @Override public synchronized LifecycleOutcome activate(OwnerRef owner, UUID id) {
        StoredRun selected = owned(owner, id); requireVisible(selected);
        if (selected.status() != RunStatus.SUSPENDED) return unchanged(selected);
        runs.values().stream().filter(run -> run.owner().equals(owner) && run.status() == RunStatus.ACTIVE)
                .findFirst().ifPresent(run -> runs.put(run.id(), lifecycle(run, RunStatus.SUSPENDED,
                        run.archivedAt(), run.trashedAt(), clock.instant())));
        return updateLifecycle(selected, RunStatus.ACTIVE, selected.archivedAt(), selected.trashedAt());
    }
    @Override public synchronized LifecycleOutcome archive(OwnerRef owner, UUID id) {
        StoredRun run = owned(owner, id); requireNotPreparing(run);
        RunStatus status = run.status() == RunStatus.ACTIVE ? RunStatus.SUSPENDED : run.status();
        return updateLifecycle(run, status, clock.instant(), run.trashedAt());
    }
    @Override public synchronized LifecycleOutcome unarchive(OwnerRef owner, UUID id) {
        StoredRun run = owned(owner, id); return updateLifecycle(run, run.status(), null, run.trashedAt());
    }
    @Override public synchronized LifecycleOutcome trash(OwnerRef owner, UUID id) {
        StoredRun run = owned(owner, id); requireNotPreparing(run);
        RunStatus status = run.status() == RunStatus.ACTIVE ? RunStatus.SUSPENDED : run.status();
        return updateLifecycle(run, status, run.archivedAt(), clock.instant());
    }
    @Override public synchronized LifecycleOutcome restore(OwnerRef owner, UUID id) {
        StoredRun run = owned(owner, id); return updateLifecycle(run, run.status(), run.archivedAt(), null);
    }
    @Override public synchronized LifecycleOutcome purge(OwnerRef owner, UUID id, PurgeConfirmation confirmation) {
        StoredRun run = owned(owner, id);
        if (confirmation == null || !confirmation.confirmed()) throw new IllegalStateException("PURGE_CONFIRMATION_REQUIRED");
        Duration grace = confirmation.gracePeriod() == null ? Duration.ofDays(30) : confirmation.gracePeriod();
        if (run.trashedAt() == null || (!confirmation.force() && run.trashedAt().plus(grace).isAfter(clock.instant())))
            throw new IllegalStateException("PURGE_CONFIRMATION_REQUIRED");
        delete(id, owner); return new LifecycleOutcome(id, run.status(), true);
    }

    @Override public synchronized StoreRecoveryReport recoveryReport() {
        return new StoreRecoveryReport(storeId, 0, RecoverySource.EMPTY, List.of(), List.of(), false);
    }
    @Override public synchronized List<RecoverableRun> scanRecoverable() {
        Instant now = clock.instant();
        return runs.values().stream().map(run -> new RecoverableRun(run.id(), run.revision(),
                liveLease(run) ? RecoveryState.WAITING_FOR_LEASE
                        : hasInterrupted(run, now) ? RecoveryState.INTERRUPTED_COMMAND : RecoveryState.NO_ACTION,
                hasInterrupted(run, now) ? "COMMAND_INTERRUPTED" : null)).toList();
    }
    @Override public synchronized boolean recoverInterrupted(UUID runId, Instant now) {
        StoredRun run = runs.get(runId); if (run == null || liveLease(run)) return false;
        boolean changed = false; String prefix = runId + ":";
        for (var entry : new ArrayList<>(commands.entrySet())) if (entry.getKey().startsWith(prefix)) {
            LoggedCommand command = entry.getValue();
            if (command.state() == CommandState.IN_PROGRESS && command.revisionBefore() == run.revision()) {
                commands.put(entry.getKey(), new LoggedCommand(command.payloadMac(), CommandState.FAILED, false,
                        command.revisionBefore(), null, List.of(), "COMMAND_INTERRUPTED")); changed = true;
            }
        }
        if (run.lease() != null && !run.lease().until().isAfter(now)) {
            runs.put(run.id(), copy(run, run.status(), run.revision(), run.snapshot(), run.pendingStart(),
                    null, run.failureCode(), run.generationCalls(), now)); changed = true;
        }
        return changed;
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
                                      long revisionAfter, List<Event> commandEvents,
                                      Long latencyMillis, SceneSource sceneSource) {
        String key = commandKey(runId, commandId);
        LoggedCommand prior = commands.get(key);
        if (prior == null) throw new IllegalStateException("COMMAND_NOT_BEGUN");
        commands.put(key, new LoggedCommand(prior.payloadMac(), CommandState.DONE, accepted,
                prior.revisionBefore(), revisionAfter, commandEvents, null, latencyMillis, sceneSource));
    }

    @Override
    public synchronized List<CommandSummary> list(UUID runId) {
        String prefix = runId + ":";
        return commands.entrySet().stream().filter(entry -> entry.getKey().startsWith(prefix))
                .map(entry -> {
                    LoggedCommand command = entry.getValue();
                    return new CommandSummary(UUID.fromString(entry.getKey().substring(prefix.length())),
                            command.state(), command.accepted(), command.revisionBefore(),
                            command.revisionAfter(), command.latencyMillis(), command.sceneSource());
                }).toList();
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
        if (priority == com.capybee.engine.session.MeteredTextGenerator.Priority.PREFETCH) {
            usage.computeIfAbsent(profileKey, ignored -> new long[3])[2]++;
            usage.computeIfAbsent(globalKey, ignored -> new long[3])[2]++;
            runPrefetchCalls.merge(runKey, 1, Integer::sum);
        }
        return true;
    }

    @Override
    public synchronized void recordUsage(LedgerScope profile, LedgerScope global, LocalDate day,
                                         UUID runId, TextGenerationResult.TokenUsage tokenUsage,
                                         com.capybee.engine.session.MeteredTextGenerator.Priority priority) {
        addUsage(usageKey(profile.value(), day), tokenUsage);
        addUsage(usageKey(global.value(), day), tokenUsage);
    }

    @Override
    public synchronized void releaseCall(LedgerScope profile, LedgerScope global, LocalDate day, UUID runId) {
        decrement(usageKey(profile.value(), day));
        decrement(usageKey(global.value(), day));
        runReservations.computeIfPresent(runId.toString(), (ignored, count) -> Math.max(0, count - 1));
    }

    @Override
    public synchronized DayUsage dayUsage(LedgerScope scope, LocalDate day) {
        String key = usageKey(scope.value(), day);
        long[] totals = usage.getOrDefault(key, new long[3]);
        return new DayUsage(reservations.getOrDefault(key, 0), totals[0], totals[1],
                Math.toIntExact(totals[2]));
    }

    @Override
    public synchronized int runPrefetchCalls(UUID runId) {
        return runPrefetchCalls.getOrDefault(runId.toString(), 0);
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
        if (!runs.containsKey(runId)) return false;
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
        boolean outer = transactionDepth++ == 0;
        if (outer) transactionSnapshot = snapshot();
        try { work.run(); }
        catch (Throwable failure) { if (outer) restore(transactionSnapshot); throw failure; }
        finally { transactionDepth--; if (outer) transactionSnapshot = null; }
    }

    private boolean hasLease(StoredRun run, Lease lease) {
        return run != null && run.lease() != null && lease != null
                && run.lease().token().equals(lease.token());
    }

    private StoredRun copy(StoredRun old, RunStatus status, long revision, RunSnapshot snapshot,
                           StartRequest pendingStart, Lease lease, String failureCode, int generationCalls,
                           Instant updatedAt) {
        return new StoredRun(old.id(), old.owner(), old.packId(), old.locale(), status, revision,
                snapshot, pendingStart, lease, failureCode, generationCalls, old.createdAt(), updatedAt,
                old.packVersion(), updatedAt, old.archivedAt(), old.trashedAt());
    }

    private StoredRun withGenerationCalls(StoredRun run) {
        return new StoredRun(run.id(), run.owner(), run.packId(), run.locale(), run.status(), run.revision(),
                run.snapshot(), run.pendingStart(), run.lease(), run.failureCode(),
                runReservations.getOrDefault(run.id().toString(), 0), run.createdAt(), run.updatedAt(),
                run.packVersion(), run.lastPlayedAt(), run.archivedAt(), run.trashedAt());
    }

    private StoredRun owned(OwnerRef owner, UUID id) { StoredRun run = runs.get(id); if (run == null || !run.owner().equals(owner)) throw new IllegalStateException("RUN_NOT_FOUND"); return run; }
    private void requireVisible(StoredRun run) { if (run.archivedAt() != null) throw new IllegalStateException("RUN_ARCHIVED"); if (run.trashedAt() != null) throw new IllegalStateException("RUN_TRASHED"); }
    private void requireNotPreparing(StoredRun run) { if (run.status() == RunStatus.PREPARING || liveLease(run)) throw new IllegalStateException("RUN_SWITCH_BUSY"); }
    private boolean liveLease(StoredRun run) { return run.lease() != null && run.lease().until().isAfter(clock.instant()); }
    private boolean hasInterrupted(StoredRun run, Instant now) { return !liveLease(run) && commands.entrySet().stream().anyMatch(e -> e.getKey().startsWith(run.id()+":") && e.getValue().state()==CommandState.IN_PROGRESS && e.getValue().revisionBefore()==run.revision()); }
    private static boolean isCurrent(StoredRun run) { return run.status()==RunStatus.ACTIVE || run.status()==RunStatus.PREPARING; }
    private LifecycleOutcome unchanged(StoredRun run) { return new LifecycleOutcome(run.id(), run.status(), false); }
    private LifecycleOutcome updateLifecycle(StoredRun run, RunStatus status, Instant archived, Instant trashed) { StoredRun changed=lifecycle(run,status,archived,trashed,clock.instant()); runs.put(run.id(),changed); return new LifecycleOutcome(run.id(),status,true); }
    private StoredRun lifecycle(StoredRun run, RunStatus status, Instant archived, Instant trashed, Instant now) { return new StoredRun(run.id(),run.owner(),run.packId(),run.locale(),status,run.revision(),run.snapshot(),run.pendingStart(),null,run.failureCode(),run.generationCalls(),run.createdAt(),now,run.packVersion(),now,archived,trashed); }
    private RunDescriptor descriptor(StoredRun run) { return new RunDescriptor(run.id(),run.packId(),run.packVersion(),run.locale(),run.status(),run.revision(),null,run.createdAt(),run.lastPlayedAt(),run.status()==RunStatus.ENDED?run.updatedAt():null,run.archivedAt(),run.trashedAt(),run.status()==RunStatus.ACTIVE||run.status()==RunStatus.SUSPENDED,run.failureCode(),0); }
    private static PageToken encodeToken(int offset, OwnerRef owner, RunQuery query) { return new PageToken(Base64.getUrlEncoder().withoutPadding().encodeToString((owner.value()+"|"+query.includeArchived()+"|"+query.includeTrashed()+"|"+offset).getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
    private static int decodeToken(PageToken token, OwnerRef owner, RunQuery query) { if(token==null)return 0; try { String[] p=new String(Base64.getUrlDecoder().decode(token.value()),java.nio.charset.StandardCharsets.UTF_8).split("\\|",-1); if(p.length!=4||!p[0].equals(owner.value())||Boolean.parseBoolean(p[1])!=query.includeArchived()||Boolean.parseBoolean(p[2])!=query.includeTrashed())throw new IllegalArgumentException("invalid page token"); return Integer.parseInt(p[3]); } catch(RuntimeException e){throw new IllegalArgumentException("invalid page token");} }
    private Snapshot snapshot(){return new Snapshot(new HashMap<>(runs),new LinkedHashMap<>(commands),copyEvents(),copyPrefetch(),new HashMap<>(reservations),new HashMap<>(runReservations),new HashMap<>(runPrefetchCalls),copyUsage());}
    private Map<UUID,List<OutboxEvent>> copyEvents(){Map<UUID,List<OutboxEvent>> copy=new HashMap<>();events.forEach((k,v)->copy.put(k,new ArrayList<>(v)));return copy;}
    private Map<UUID,Map<String,PrefetchedArtifact>> copyPrefetch(){Map<UUID,Map<String,PrefetchedArtifact>> copy=new HashMap<>();prefetch.forEach((k,v)->copy.put(k,new HashMap<>(v)));return copy;}
    private Map<String,long[]> copyUsage(){Map<String,long[]> copy=new HashMap<>();usage.forEach((k,v)->copy.put(k,v.clone()));return copy;}
    private void restore(Snapshot s){runs.clear();runs.putAll(s.runs);commands.clear();commands.putAll(s.commands);events.clear();events.putAll(s.events);prefetch.clear();prefetch.putAll(s.prefetch);reservations.clear();reservations.putAll(s.reservations);runReservations.clear();runReservations.putAll(s.runReservations);runPrefetchCalls.clear();runPrefetchCalls.putAll(s.runPrefetchCalls);usage.clear();usage.putAll(s.usage);}
    private record Snapshot(Map<UUID,StoredRun> runs,Map<String,LoggedCommand> commands,Map<UUID,List<OutboxEvent>> events,Map<UUID,Map<String,PrefetchedArtifact>> prefetch,Map<String,Integer> reservations,Map<String,Integer> runReservations,Map<String,Integer> runPrefetchCalls,Map<String,long[]> usage){}

    private void addUsage(String key, TextGenerationResult.TokenUsage value) {
        long[] totals = usage.computeIfAbsent(key, ignored -> new long[3]);
        totals[0] += value.inputTokens();
        totals[1] += value.outputTokens();
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
