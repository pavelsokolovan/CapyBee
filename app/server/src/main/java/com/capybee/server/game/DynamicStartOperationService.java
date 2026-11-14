package com.capybee.server.game;

import com.capybee.engine.session.ConfirmedDynamicPreparation;
import com.capybee.engine.session.DynamicStartCoordinator;
import com.capybee.engine.session.SessionTypes.OwnerRef;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/** Process-local, owner-scoped wait-then-poll operation registry. Preparations remain memory-only. */
public final class DynamicStartOperationService {
    private record Key(String owner, UUID requestId) {}
    private record Operation(String signature, CompletableFuture<DynamicStartCoordinator.Result> future,
                             DynamicStartCoordinator.Cancellation cancellation, long createdAt) {}
    public sealed interface Submission permits Processing, Terminal, Conflict {}
    public record Processing(long retryAfterMs) implements Submission {}
    public record Terminal(DynamicStartCoordinator.Result result) implements Submission {}
    public record Conflict() implements Submission {}

    private final DynamicStartCoordinator coordinator;
    private final Executor executor;
    private final Duration timeout;
    private final ConcurrentHashMap<Key, Operation> operations = new ConcurrentHashMap<>();

    public DynamicStartOperationService(DynamicStartCoordinator coordinator, Executor executor, Duration timeout) {
        this.coordinator = Objects.requireNonNull(coordinator); this.executor = Objects.requireNonNull(executor);
        this.timeout = Objects.requireNonNull(timeout);
    }

    public Submission submit(OwnerRef owner, UUID requestId, ConfirmedDynamicPreparation preparation,
                             Long seed, UUID expectedCurrentRunId, Duration wait) {
        Key key = new Key(owner.value(), requestId);
        long now = System.nanoTime();
        operations.entrySet().removeIf(entry -> entry.getValue().future().isDone()
                && now - entry.getValue().createdAt() > Duration.ofMinutes(10).toNanos());
        String signature = preparation.toString() + "|" + preparation.preferences() + "|" + preparation.constraints()
                + "|" + seed + "|" + expectedCurrentRunId;
        if (!operations.containsKey(key) && operations.size() >= 1000)
            return new Terminal(new DynamicStartCoordinator.Failed("DYNAMIC_START_BUSY"));
        Operation candidate = new Operation(signature, new CompletableFuture<>(),
                new DynamicStartCoordinator.Cancellation(), now);
        Operation existing = operations.putIfAbsent(key, candidate);
        Operation operation = existing == null ? candidate : existing;
        if (existing != null && !existing.signature().equals(signature)) return new Conflict();
        if (existing == null) {
            try {
                executor.execute(() -> {
                    try {
                        UUID internalId = UUID.nameUUIDFromBytes((owner.value() + ":" + requestId).getBytes(StandardCharsets.UTF_8));
                        candidate.future().complete(coordinator.startDynamic(owner, internalId, preparation, seed,
                                expectedCurrentRunId, timeout, candidate.cancellation()));
                    } catch (RuntimeException failure) {
                        candidate.future().complete(new DynamicStartCoordinator.Failed("DYNAMIC_START_UNAVAILABLE"));
                    }
                });
            } catch (RuntimeException failure) {
                candidate.future().complete(new DynamicStartCoordinator.Failed("DYNAMIC_START_UNAVAILABLE"));
            }
        }
        try {
            return new Terminal(operation.future().get(Math.max(0, wait.toMillis()), java.util.concurrent.TimeUnit.MILLISECONDS));
        } catch (java.util.concurrent.TimeoutException timeout) {
            return new Processing(1000);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); return new Processing(1000);
        } catch (java.util.concurrent.ExecutionException failed) {
            return new Terminal(new DynamicStartCoordinator.Failed("DYNAMIC_START_UNAVAILABLE"));
        }
    }

    public Submission status(OwnerRef owner, UUID requestId) {
        Operation operation = operations.get(new Key(owner.value(), requestId));
        if (operation == null) return null;
        if (!operation.future().isDone()) return new Processing(1000);
        try { return new Terminal(operation.future().join()); }
        catch (RuntimeException failure) { return new Terminal(new DynamicStartCoordinator.Failed("DYNAMIC_START_UNAVAILABLE")); }
    }

    public boolean exists(OwnerRef owner, UUID requestId) {
        return operations.containsKey(new Key(owner.value(), requestId));
    }

    public boolean cancel(OwnerRef owner, UUID requestId) {
        Operation operation = operations.get(new Key(owner.value(), requestId));
        if (operation == null) return false;
        if (operation.cancellation().cancel())
            operation.future().complete(new DynamicStartCoordinator.Cancelled("DYNAMIC_CANCELLED"));
        return true;
    }
}
