package com.capybee.server.game;

import com.capybee.engine.session.SessionException;
import com.capybee.engine.session.SessionTypes.ErrorCode;
import com.capybee.engine.session.SessionTypes.OwnerRef;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

final class GameCommandRateLimiter {
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private final Clock clock;
    private final int limit;
    private final Map<String, ArrayDeque<Instant>> commandsByOwner = new HashMap<>();

    GameCommandRateLimiter(Clock clock, int limit) {
        this.clock = clock;
        this.limit = Math.max(1, limit);
    }

    synchronized void check(OwnerRef owner) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(WINDOW);
        commandsByOwner.values().forEach(times -> {
            while (!times.isEmpty() && !times.peekFirst().isAfter(cutoff)) {
                times.removeFirst();
            }
        });
        commandsByOwner.values().removeIf(ArrayDeque::isEmpty);
        ArrayDeque<Instant> times = commandsByOwner.computeIfAbsent(owner.value(),
                ignored -> new ArrayDeque<>());
        if (times.size() >= limit) {
            Duration remaining = Duration.between(now, times.peekFirst().plus(WINDOW));
            long retryAfterSeconds = Math.max(1, remaining.toSeconds()
                    + (remaining.getNano() == 0 ? 0 : 1));
            throw new SessionException(ErrorCode.RATE_LIMITED,
                    Map.of("retryAfterSeconds", retryAfterSeconds));
        }
        times.addLast(now);
    }
}
