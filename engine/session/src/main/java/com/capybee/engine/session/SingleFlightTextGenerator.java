package com.capybee.engine.session;

import com.capybee.engine.port.StructuredGenerationRequest;
import com.capybee.engine.port.TextGenerationResult;
import com.capybee.engine.port.TextGenerator;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class SingleFlightTextGenerator implements TextGenerator {
    private static final long JOIN_TIMEOUT_SECONDS = 120;

    private final TextGenerator delegate;
    private final SingleFlightRegistry registry;
    private final UUID runId;

    public SingleFlightTextGenerator(TextGenerator delegate, SingleFlightRegistry registry, UUID runId) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.runId = Objects.requireNonNull(runId, "runId");
    }

    @Override
    public TextGenerationResult generate(StructuredGenerationRequest request) {
        if (request.slotKey() == null) return delegate.generate(request);
        String key = runId + ":" + request.slotKey();
        CompletableFuture<TextGenerationResult> existing;
        while (true) {
            CompletableFuture<TextGenerationResult> leader = registry.register(key);
            if (leader != null) return generateAsLeader(key, leader, request);
            existing = registry.inFlight(key);
            if (existing != null) break;
        }
        try {
            TextGenerationResult result = existing.get(JOIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            registry.markJoined();
            return result;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return delegate.generate(request);
        } catch (ExecutionException failure) {
            return retryFailedLeader(key, request);
        } catch (TimeoutException timeout) {
            return delegate.generate(request);
        }
    }

    private TextGenerationResult retryFailedLeader(String key, StructuredGenerationRequest request) {
        while (true) {
            CompletableFuture<TextGenerationResult> leader = registry.register(key);
            if (leader != null) return generateAsLeader(key, leader, request);
            CompletableFuture<TextGenerationResult> existing = registry.inFlight(key);
            if (existing == null) continue;
            if (existing.isCompletedExceptionally()) {
                registry.remove(key, existing);
                continue;
            }
            try {
                TextGenerationResult result = existing.get(JOIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                registry.markJoined();
                return result;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return delegate.generate(request);
            } catch (ExecutionException | TimeoutException failure) {
                return delegate.generate(request);
            }
        }
    }

    private TextGenerationResult generateAsLeader(String key, CompletableFuture<TextGenerationResult> future,
                                                   StructuredGenerationRequest request) {
        try {
            TextGenerationResult result = delegate.generate(request);
            future.complete(result);
            return result;
        } catch (RuntimeException failure) {
            future.completeExceptionally(failure);
            throw failure;
        } finally {
            registry.remove(key, future);
        }
    }
}
