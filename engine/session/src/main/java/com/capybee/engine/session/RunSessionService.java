package com.capybee.engine.session;

import com.capybee.engine.api.Command;
import com.capybee.engine.api.Engine;
import com.capybee.engine.api.Event;
import com.capybee.engine.api.ResumeResult;
import com.capybee.engine.api.Run;
import com.capybee.engine.api.RunSnapshot;
import com.capybee.engine.api.RunState;
import com.capybee.engine.api.StartResult;
import com.capybee.engine.api.StepResult;
import com.capybee.engine.generation.GenerationArtifact;
import com.capybee.engine.pack.Pack;
import com.capybee.engine.pack.PackFeature;
import com.capybee.engine.pack.PackId;
import com.capybee.engine.session.SessionPorts.CommandLog;
import com.capybee.engine.session.SessionPorts.EngineFactory;
import com.capybee.engine.session.SessionPorts.EventOutbox;
import com.capybee.engine.session.SessionPorts.PackCatalog;
import com.capybee.engine.session.SessionPorts.PrefetchStore;
import com.capybee.engine.session.SessionPorts.RunRepository;
import com.capybee.engine.session.SessionPorts.SessionClock;
import com.capybee.engine.session.SessionPorts.Transaction;
import com.capybee.engine.session.wire.PackInfoDto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.capybee.engine.session.SessionPorts.CreateKind;
import static com.capybee.engine.session.SessionPorts.InsertKind;
import static com.capybee.engine.session.SessionTypes.*;

public final class RunSessionService {
    private final PackCatalog packs;
    private final RunRepository runs;
    private final CommandLog commands;
    private final EventOutbox outbox;
    private final PrefetchStore prefetch;
    private final EngineFactory engines;
    private final SessionClock clock;
    private final SessionLimits limits;
    private final Transaction transaction;
    private final Executor workers;
    private final byte[] commandMacKey;
    private final Semaphore generationSlots;
    private final Map<UUID, CompletableFuture<Void>> startJobs = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<GenerationArtifact>> prefetchJobs = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<CommandOutcome>> commandJobs = new ConcurrentHashMap<>();

    public RunSessionService(PackCatalog packs, RunRepository runs, CommandLog commands,
                             EventOutbox outbox, PrefetchStore prefetch, EngineFactory engines,
                             SessionClock clock, SessionLimits limits, Transaction transaction,
                             Executor workers, byte[] commandMacKey) {
        this.packs = packs;
        this.runs = runs;
        this.commands = commands;
        this.outbox = outbox;
        this.prefetch = prefetch;
        this.engines = engines;
        this.clock = clock;
        this.limits = limits;
        this.transaction = transaction;
        this.workers = workers;
        this.commandMacKey = commandMacKey.clone();
        this.generationSlots = new Semaphore(limits.maxConcurrentGeneration());
    }

    public StartAccepted start(OwnerRef owner, StartRequest request) {
        return start(owner, request, Duration.ZERO);
    }

    public StartAccepted start(OwnerRef owner, StartRequest request, Duration syncWait) {
        if (request == null || request.id() == null || request.packId() == null || request.locale() == null) {
            throw new SessionException(ErrorCode.REQUEST_INVALID);
        }
        if (packs.find(request.packId().value()).isEmpty()) {
            throw new SessionException(ErrorCode.REQUEST_INVALID);
        }
        Instant now = clock.now();
        Pack selectedPack = packs.find(request.packId().value()).orElseThrow(() ->
                new SessionException(ErrorCode.REQUEST_INVALID));
        var created = runs.createPreparing(new SessionTypes.NewRun(request.id(), owner, request.packId(),
                selectedPack.manifest().version().toString(), request.locale(),
                request.startInputs(), request.seed(), request.generated(), now));
        if (created.kind() == CreateKind.EXISTS_OTHER_OWNER) {
            throw new SessionException(ErrorCode.RUN_NOT_FOUND);
        }
        if (created.kind() == CreateKind.OWNER_HAS_ACTIVE) {
            throw new SessionException(ErrorCode.RUN_ALREADY_ACTIVE,
                    Map.of("existingRunId", created.existingRunId().toString()));
        }
        StoredRun stored = runs.find(request.id(), owner).orElseThrow(() ->
                new SessionException(ErrorCode.RUN_NOT_FOUND));
        CompletableFuture<Void> startJob = null;
        if (created.kind() == CreateKind.CREATED) {
            startJob = scheduleStart(owner, request);
        }
        if (startJob != null) waitForStart(startJob, syncWait);
        stored = runs.find(request.id(), owner).orElse(stored);
        if (stored.status() == RunStatus.FAILED) {
            throw new SessionException(stored.failureCode() == null ? ErrorCode.START_TIMEOUT
                    : ErrorCode.valueOf(stored.failureCode()));
        }
        return new StartAccepted(stored.id(), stored.status(), stored.revision(), 1500);
    }

    public RunSummary get(OwnerRef owner, UUID runId) {
        StoredRun stored = requireRun(owner, runId);
        if (stored.status() == RunStatus.PREPARING) {
            if (stored.createdAt().plus(limits.startTtl()).isBefore(clock.now())) {
                Lease lease = runs.tryClaim(runId, owner, limits.leaseDuration()).orElse(null);
                if (lease != null) runs.markFailed(runId, lease, "START_TIMEOUT");
                stored = requireRun(owner, runId);
            } else if (stored.lease() == null || !stored.lease().until().isAfter(clock.now())) {
                scheduleStart(owner, stored.pendingStart());
            }
        }
        RunSummary result = summary(stored);
        if (stored.status() == RunStatus.ACTIVE) ensurePrefetch(owner, runId);
        return result;
    }

    public RunSummary current(OwnerRef owner) {
        StoredRun stored = runs.findCurrent(owner)
                .orElseThrow(() -> new SessionException(ErrorCode.RUN_NOT_FOUND));
        return get(owner, stored.id());
    }

    public List<OutboxEvent> events(OwnerRef owner, UUID runId, long afterSeq, int limit) {
        requireRun(owner, runId);
        if (afterSeq < 0 || limit < 1 || limit > 100) {
            throw new SessionException(ErrorCode.REQUEST_INVALID);
        }
        return outbox.readAfter(runId, afterSeq, limit);
    }

    public CommandOutcome command(OwnerRef owner, UUID runId, CommandRequest request, Duration syncWait) {
        if (request == null || request.id() == null || request.command() == null) {
            throw new SessionException(ErrorCode.REQUEST_INVALID);
        }
        StoredRun stored = requireRun(owner, runId);
        byte[] payloadMac = payloadMac(request.command());
        var prior = commands.find(runId, request.id());
        if (prior.isPresent()) {
            LoggedCommand logged = prior.get();
            if (!java.security.MessageDigest.isEqual(logged.payloadMac(), payloadMac)) {
                throw new SessionException(ErrorCode.COMMAND_ID_REUSED);
            }
            if (logged.state() == CommandState.DONE) {
                StoredRun current = requireRun(owner, runId);
                return new CommandOutcome(request.id(), Outcome.REPLAYED, Boolean.TRUE.equals(logged.accepted()),
                        logged.revisionAfter() == null ? logged.revisionBefore() : logged.revisionAfter(),
                        current.revision(), current.status(), current.snapshot() == null ? null
                        : resume(owner, mergePrefetched(current.snapshot(), prefetch.load(runId))).run().view(),
                        logged.events(), List.of());
            }
            if (logged.state() == CommandState.FAILED) {
                throw new SessionException(errorCode(logged.failureCode()));
            }
            CompletableFuture<CommandOutcome> existing = commandJobs.get(commandKey(runId, request.id()));
            if (existing != null && !existing.isDone()) {
                return waitForCommand(existing, request.id(), syncWait, stored);
            }
        } else {
            if (stored.status() != RunStatus.ACTIVE) {
                throw new SessionException(ErrorCode.RUN_NOT_ACTIVE, Map.of("status", stored.status().name()));
            }
            if (request.expectedRevision() != stored.revision()) {
                throw new SessionException(ErrorCode.REVISION_CONFLICT,
                        Map.of("currentRevision", stored.revision()));
            }
            if (commands.begin(runId, request.id(), payloadMac, stored.revision()) == InsertKind.EXISTS) {
                return command(owner, runId, request, syncWait);
            }
        }
        CompletableFuture<CommandOutcome> job = new CompletableFuture<>();
        String key = commandKey(runId, request.id());
        CompletableFuture<CommandOutcome> raced = commandJobs.putIfAbsent(key, job);
        if (raced != null) return waitForCommand(raced, request.id(), syncWait, stored);
        try {
            workers.execute(() -> processCommand(owner, runId, request, job, syncWait));
        } catch (RuntimeException failure) {
            commandJobs.remove(key, job);
            commands.fail(runId, request.id(), "GENERATION_BUSY");
            throw new SessionException(ErrorCode.GENERATION_BUSY);
        }
        return waitForCommand(job, request.id(), syncWait, stored);
    }

    public CommandStatus commandStatus(OwnerRef owner, UUID runId, UUID commandId) {
        StoredRun stored = requireRun(owner, runId);
        LoggedCommand logged = commands.find(runId, commandId).orElseThrow(() ->
                new SessionException(ErrorCode.RUN_NOT_FOUND));
        if (logged.state() == CommandState.DONE) {
            return new CommandStatus(false, replay(owner, stored, commandId, logged), 0);
        }
        if (logged.state() == CommandState.FAILED) {
            throw new SessionException(errorCode(logged.failureCode()));
        }
        CompletableFuture<CommandOutcome> job = commandJobs.get(commandKey(runId, commandId));
        if (job != null && job.isDone() && !job.isCompletedExceptionally()) {
            return new CommandStatus(false, job.join(), 0);
        }
        return new CommandStatus(true, null, 1500);
    }

    public void delete(OwnerRef owner, UUID runId) {
        requireRun(owner, runId);
        runs.delete(runId, owner);
        String prefix = runId + ":";
        prefetchJobs.entrySet().removeIf(entry -> entry.getKey().startsWith(prefix));
    }

    public List<PackInfoDto> packs(java.util.Locale hint) {
        return packs.all().stream().sorted(Comparator.comparing(pack -> pack.manifest().id().value()))
                .map(pack -> new PackInfoDto(pack.manifest().id().value(), pack.manifest().version().toString(),
                        pack.manifest().locales().stream().map(locale -> locale.value()).toList(),
                        pack.manifest().defaultLocale().value(), pack.manifest().startInputs(),
                        pack.manifest().features().contains(PackFeature.GENERATION)))
                .toList();
    }

    public void ensurePrefetch(OwnerRef owner, UUID runId) {
        try {
            StoredRun stored = requireRun(owner, runId);
            if (stored.status() != RunStatus.ACTIVE || stored.snapshot() == null) return;
            ResumeResult resumed = engines.create(owner, runId, MeteredTextGenerator.Priority.PREFETCH)
                    .resume(mergePrefetched(stored.snapshot(), prefetch.load(runId)));
            if (!resumed.ok()) return;
            Run run = resumed.run();
            for (String sceneId : run.prefetchCandidates().stream().limit(3).toList()) {
                String key = runId + ":" + sceneId;
                CompletableFuture<GenerationArtifact> future = new CompletableFuture<>();
                if (prefetchJobs.putIfAbsent(key, future) != null) continue;
                try {
                    workers.execute(() -> {
                        try {
                            if (!generationSlots.tryAcquire()) {
                                future.completeExceptionally(new IllegalStateException("GENERATION_BUSY"));
                                return;
                            }
                            try {
                                GenerationArtifact artifact = run.prefetch(sceneId).orElse(null);
                                if (artifact != null && artifact.outcome() != GenerationArtifact.Outcome.FALLBACK) {
                                    prefetch.insertIfAbsent(runId, sceneId, stored.revision(), artifact);
                                }
                                future.complete(artifact);
                            } finally {
                                generationSlots.release();
                            }
                        } catch (RuntimeException failure) {
                            future.completeExceptionally(failure);
                        } finally {
                            prefetchJobs.remove(key, future);
                        }
                    });
                } catch (RuntimeException rejected) {
                    prefetchJobs.remove(key, future);
                    future.completeExceptionally(rejected);
                }
            }
        } catch (RuntimeException ignored) {
            // Prefetch is an optimisation and must never fail a run read.
        }
    }

    public Diagnostics diagnostics(OwnerRef owner, UUID runId) {
        StoredRun stored = requireRun(owner, runId);
        Set<String> ready = new HashSet<>();
        prefetch.load(runId).forEach(item -> ready.add(item.sceneId()));
        List<String> inFlight = prefetchJobs.keySet().stream()
                .filter(key -> key.startsWith(runId + ":"))
                .map(key -> key.substring((runId + ":").length())).sorted().toList();
        int calls = stored.snapshot() == null ? 0 : resume(owner, stored.snapshot()).run()
                .generationSummary().map(summary -> summary.totalProviderCalls()).orElse(0);
        return new Diagnostics(runId, stored.revision(), calls, limits.runCalls(),
                ready.stream().sorted().toList(), inFlight);
    }

    private CompletableFuture<Void> scheduleStart(OwnerRef owner, StartRequest request) {
        if (request == null) return CompletableFuture.completedFuture(null);
        CompletableFuture<Void> job = new CompletableFuture<>();
        CompletableFuture<Void> current = startJobs.putIfAbsent(request.id(), job);
        if (current != null && !current.isDone()) return current;
        if (current != null && !startJobs.replace(request.id(), current, job)) {
            return scheduleStart(owner, request);
        }
        try {
            workers.execute(() -> {
                try {
                    processStart(owner, request);
                    job.complete(null);
                } catch (RuntimeException failure) {
                    job.completeExceptionally(failure);
                } finally {
                    startJobs.remove(request.id(), job);
                }
            });
        } catch (RuntimeException failure) {
            startJobs.remove(request.id(), job);
            job.completeExceptionally(failure);
            throw new SessionException(ErrorCode.GENERATION_BUSY);
        }
        return job;
    }

    private void waitForStart(CompletableFuture<Void> job, Duration requestedWait) {
        Duration wait = requestedWait == null || requestedWait.isNegative() ? Duration.ZERO
                : requestedWait.compareTo(Duration.ofSeconds(20)) > 0
                ? Duration.ofSeconds(20) : requestedWait;
        try {
            job.get(wait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeout) {
            return;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (java.util.concurrent.ExecutionException failure) {
            // The run remains PREPARING and can be safely retried by a later poll.
        }
    }

    private void processStart(OwnerRef owner, StartRequest request) {
        if (!generationSlots.tryAcquire()) {
            return;
        }
        Lease lease = runs.tryClaim(request.id(), owner, limits.leaseDuration()).orElse(null);
        if (lease == null) {
            generationSlots.release();
            return;
        }
        try {
            Engine engine = engines.create(owner, request.id(), MeteredTextGenerator.Priority.DEMAND);
            StartResult result = engine.start(new com.capybee.engine.api.RunRequest(
                    request.packId(), request.locale(), request.startInputs(), request.seed(), request.generated()));
            if (!result.ok()) {
                runs.markFailed(request.id(), lease, "START_REJECTED");
                return;
            }
            RunSnapshot snapshot = withRunId(result.run().snapshot(), request.id());
            transaction.execute(() -> {
                if (!runs.saveActive(request.id(), lease, 0, snapshot, result.events())) {
                    throw new SessionException(ErrorCode.RUN_BUSY);
                }
                outbox.append(request.id(), null, 0, result.events());
            });
            ensurePrefetch(owner, request.id());
        } catch (SessionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            runs.releaseLease(request.id(), lease);
        } finally {
            generationSlots.release();
        }
    }

    private void processCommand(OwnerRef owner, UUID runId, CommandRequest request,
                                CompletableFuture<CommandOutcome> resultFuture, Duration syncWait) {
        Lease lease = null;
        try {
            awaitInFlightPrefetch(runId, syncWait);
            StoredRun stored = requireRun(owner, runId);
            if (stored.status() != RunStatus.ACTIVE) {
                throw new SessionException(ErrorCode.RUN_NOT_ACTIVE, Map.of("status", stored.status().name()));
            }

            if (stored.revision() != request.expectedRevision()) {
                throw new SessionException(ErrorCode.REVISION_CONFLICT,
                        Map.of("currentRevision", stored.revision()));
            }
            lease = runs.tryClaim(runId, owner, limits.leaseDuration()).orElseThrow(() ->
                    new SessionException(ErrorCode.RUN_BUSY));
            Lease claimedLease = lease;
            if (!generationSlots.tryAcquire()) throw new SessionException(ErrorCode.GENERATION_BUSY);
            try {
                RunSnapshot merged = mergePrefetched(stored.snapshot(), prefetch.load(runId));
                ResumeResult resumed = engines.create(owner, runId, MeteredTextGenerator.Priority.DEMAND)
                        .resume(merged);
                if (!resumed.ok() && merged != stored.snapshot()) {
                    prefetch.deleteForScenes(runId, prefetch.load(runId).stream()
                            .map(PrefetchedArtifact::sceneId).toList());
                    resumed = engines.create(owner, runId, MeteredTextGenerator.Priority.DEMAND)
                            .resume(stored.snapshot());
                }
                if (!resumed.ok()) throw new SessionException(ErrorCode.RUN_UNRECOVERABLE);
                Run run = resumed.run();
                StepResult step = run.apply(request.command());
                RunSnapshot nextSnapshot = step.accepted() ? run.snapshot() : stored.snapshot();
                long nextRevision = stored.revision() + (step.accepted() ? 1 : 0);
                transaction.execute(() -> {
                    if (step.accepted() && !runs.saveActive(runId, claimedLease, stored.revision(),
                            nextSnapshot, step.events())) {
                        throw new SessionException(ErrorCode.REVISION_CONFLICT,
                                Map.of("currentRevision", stored.revision()));
                    }
                    if (step.accepted()) {
                        outbox.append(runId, request.id(), nextRevision, step.events());
                        prefetch.deleteForScenes(runId, visitedGeneratedScenes(nextSnapshot));
                    }
                    commands.complete(runId, request.id(), step.accepted(), nextRevision, step.events());
                });
                runs.releaseLease(runId, claimedLease);
                StoredRun current = requireRun(owner, runId);
                CommandOutcome outcome = new CommandOutcome(request.id(),
                        step.accepted() ? Outcome.APPLIED : Outcome.REJECTED, step.accepted(),
                        nextRevision, current.revision(), current.status(), step.view(), step.events(),
                        step.issues());
                resultFuture.complete(outcome);
                if (current.status() == RunStatus.ACTIVE) ensurePrefetch(owner, runId);
            } finally {
                generationSlots.release();
            }
        } catch (RuntimeException failure) {
            if (failure instanceof StoreException) {
                if (lease != null) {
                    try { runs.releaseLease(runId, lease); } catch (StoreException ignored) { }
                }
                resultFuture.completeExceptionally(new SessionException(ErrorCode.SERVICE_UNAVAILABLE));
                return;
            }
            if (failure instanceof SessionException sessionException) {
                if (sessionException.code() == ErrorCode.RUN_UNRECOVERABLE && lease != null) {
                    Lease failedLease = lease;
                    transaction.execute(() -> {
                        runs.markFailed(runId, failedLease, ErrorCode.RUN_UNRECOVERABLE.name());
                        commands.fail(runId, request.id(), ErrorCode.RUN_UNRECOVERABLE.name());
                    });
                } else if (lease != null) {
                    runs.releaseLease(runId, lease);
                }
                resultFuture.completeExceptionally(sessionException);
            } else {
                if (lease != null) runs.releaseLease(runId, lease);
                resultFuture.completeExceptionally(new SessionException(ErrorCode.RUN_UNRECOVERABLE));
            }
        } finally {
            commandJobs.remove(commandKey(runId, request.id()), resultFuture);
        }
    }

    private void awaitInFlightPrefetch(UUID runId, Duration syncWait) {
        if (syncWait == null || syncWait.isZero() || syncWait.isNegative()) return;
        Duration boundedWait = syncWait.compareTo(Duration.ofSeconds(20)) > 0
                ? Duration.ofSeconds(20) : syncWait;
        long deadline = System.nanoTime() + boundedWait.toNanos();
        String prefix = runId + ":";
        for (Map.Entry<String, CompletableFuture<GenerationArtifact>> entry
                : List.copyOf(prefetchJobs.entrySet())) {
            if (!entry.getKey().startsWith(prefix)) continue;
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) return;
            try {
                entry.getValue().get(remaining, TimeUnit.NANOSECONDS);
            } catch (TimeoutException timeout) {
                return;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } catch (java.util.concurrent.ExecutionException ignored) {
                // A failed speculative request is retried on demand.
            }
        }
    }

    private CommandOutcome waitForCommand(CompletableFuture<CommandOutcome> job, UUID commandId,
                                          Duration requestedWait, StoredRun stored) {
        Duration wait = requestedWait == null ? Duration.ZERO
                : requestedWait.compareTo(Duration.ofSeconds(20)) > 0
                ? Duration.ofSeconds(20) : requestedWait;
        try {
            return job.get(Math.max(0, wait.toMillis()), TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeout) {
            return new CommandOutcome(commandId, Outcome.PROCESSING, false, stored.revision(),
                    stored.revision(), stored.status(), null, List.of(), List.of());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return new CommandOutcome(commandId, Outcome.PROCESSING, false, stored.revision(),
                    stored.revision(), stored.status(), null, List.of(), List.of());
        } catch (java.util.concurrent.ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("COMMAND_FAILED", cause);
        }
    }

    private CommandOutcome replay(OwnerRef owner, StoredRun stored, UUID commandId, LoggedCommand logged) {
        ViewAndStatus view = stored.snapshot() == null ? null
                : view(owner, mergePrefetched(stored.snapshot(), prefetch.load(stored.id())));
        boolean accepted = Boolean.TRUE.equals(logged.accepted());
        return new CommandOutcome(commandId, Outcome.REPLAYED, accepted,
                logged.revisionAfter() == null ? logged.revisionBefore() : logged.revisionAfter(),
                stored.revision(), stored.status(), view == null ? null : view.view(),
                logged.events(), List.of());
    }

    private StoredRun requireRun(OwnerRef owner, UUID runId) {
        return runs.find(runId, owner).orElseThrow(() -> new SessionException(ErrorCode.RUN_NOT_FOUND));
    }

    private RunSummary summary(StoredRun stored) {
        if (stored.snapshot() == null) {
            return new RunSummary(stored.id(), stored.status(), stored.revision(), stored.packId(),
                    null, stored.locale(), null, stored.failureCode(), null, stored.updatedAt());
        }
        ViewAndStatus view = view(stored.owner(), stored.snapshot());
        return new RunSummary(stored.id(), stored.status(), stored.revision(), stored.packId(),
                stored.snapshot().pack().version().toString(), stored.locale(),
                stored.snapshot().state().endReason() == null ? null
                        : stored.snapshot().state().endReason().name(),
                stored.failureCode(), view.view(), stored.updatedAt());
    }

    private ViewAndStatus view(OwnerRef owner, RunSnapshot snapshot) {
        ResumeResult resumed = resume(owner, snapshot, null);
        if (!resumed.ok()) throw new SessionException(ErrorCode.RUN_UNRECOVERABLE);
        return new ViewAndStatus(resumed.run().view());
    }

    private ResumeResult resume(OwnerRef owner, RunSnapshot snapshot) {
        return engines.create(owner, UUID.fromString(snapshot.runId()), MeteredTextGenerator.Priority.DEMAND)
                .resume(snapshot);
    }

    private ResumeResult resume(OwnerRef owner, RunSnapshot snapshot, UUID runId) {
        return engines.create(owner, runId == null ? UUID.fromString(snapshot.runId()) : runId,
                MeteredTextGenerator.Priority.DEMAND).resume(snapshot);
    }

    private RunSnapshot withRunId(RunSnapshot snapshot, UUID runId) {
        return new RunSnapshot(snapshot.snapshotVersion(), runId.toString(), snapshot.pack(), snapshot.locale(),
                snapshot.seed(), snapshot.startInputs(), snapshot.createdAt(), snapshot.updatedAt(),
                snapshot.step(), snapshot.state());
    }

    private RunSnapshot mergePrefetched(RunSnapshot snapshot, List<PrefetchedArtifact> artifacts) {
        if (snapshot == null || artifacts.isEmpty()) return snapshot;
        Map<String, GenerationArtifact> bySlot = new HashMap<>();
        snapshot.state().generationArtifacts().forEach(item -> bySlot.put(slot(item), item));
        artifacts.forEach(item -> bySlot.putIfAbsent(slot(item.artifact()), item.artifact()));
        RunState old = snapshot.state();
        RunState merged = new RunState(old.sceneId(), old.visited(), old.ended(), old.endReason(),
                old.counters(), old.flags(), old.sets(), old.objectives(), old.outcome(),
                old.currentPlaceId(), old.visitedPlaces(), new ArrayList<>(bySlot.values()));
        return new RunSnapshot(snapshot.snapshotVersion(), snapshot.runId(), snapshot.pack(),
                snapshot.locale(), snapshot.seed(), snapshot.startInputs(), snapshot.createdAt(),
                snapshot.updatedAt(), snapshot.step(), merged);
    }

    private List<String> visitedGeneratedScenes(RunSnapshot snapshot) {
        return snapshot.state().generationArtifacts().stream()
                .filter(artifact -> artifact.payload() instanceof GenerationArtifact.ScenePayload)
                .map(artifact -> ((GenerationArtifact.ScenePayload) artifact.payload()).sceneId())
                .toList();
    }

    private byte[] payloadMac(Command command) {
        String canonical = switch (command) {
            case Command.Choose choose -> "CHOOSE\n" + nullToEmpty(choose.optionId());
            case Command.Advance ignored -> "ADVANCE";
            case Command.SubmitText submitText -> "SUBMIT_TEXT\n" + nullToEmpty(submitText.text());
            case Command.Move move -> "MOVE\n" + nullToEmpty(move.placeId());
            case Command.Abandon ignored -> "ABANDON";
        };
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(commandMacKey, "HmacSHA256"));
            return mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("COMMAND_MAC_FAILED");
        }
    }

    private static String slot(GenerationArtifact artifact) {
        return artifact.stage() + ":" + artifact.templateId();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String commandKey(UUID runId, UUID commandId) {
        return runId + ":" + commandId;
    }

    private static ErrorCode errorCode(String code) {
        if (code == null) return ErrorCode.RUN_BUSY;
        try {
            return ErrorCode.valueOf(code);
        } catch (IllegalArgumentException unknown) {
            return ErrorCode.RUN_BUSY;
        }
    }

    private record ViewAndStatus(com.capybee.engine.api.View view) {}
}
