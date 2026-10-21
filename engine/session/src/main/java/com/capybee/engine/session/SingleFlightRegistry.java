package com.capybee.engine.session;

import com.capybee.engine.port.TextGenerationResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class SingleFlightRegistry {
    private final ConcurrentHashMap<String, CompletableFuture<TextGenerationResult>> inFlight =
            new ConcurrentHashMap<>();
    private final ThreadLocal<Boolean> joined = ThreadLocal.withInitial(() -> false);

    CompletableFuture<TextGenerationResult> register(String key) {
        CompletableFuture<TextGenerationResult> future = new CompletableFuture<>();
        return inFlight.putIfAbsent(key, future) == null ? future : null;
    }

    CompletableFuture<TextGenerationResult> inFlight(String key) {
        return inFlight.get(key);
    }

    void remove(String key, CompletableFuture<TextGenerationResult> future) {
        inFlight.remove(key, future);
    }

    void markJoined() {
        joined.set(true);
    }

    public boolean consumeJoined() {
        boolean wasJoined = joined.get();
        joined.remove();
        return wasJoined;
    }
}
