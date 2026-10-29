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

final class DynamicStartRateLimiter {
    private static final Duration WINDOW = Duration.ofHours(1);
    private final Clock clock;
    private final int limit;
    private final Map<String, ArrayDeque<Instant>> attempts = new HashMap<>();
    DynamicStartRateLimiter(Clock clock, int limit) { this.clock = clock; this.limit = Math.max(1, limit); }
    synchronized void check(OwnerRef owner) {
        Instant now = clock.instant(), cutoff = now.minus(WINDOW);
        ArrayDeque<Instant> times = attempts.computeIfAbsent(owner.value(), ignored -> new ArrayDeque<>());
        while (!times.isEmpty() && !times.peekFirst().isAfter(cutoff)) times.removeFirst();
        if (times.size() >= limit) {
            long retry = Math.max(1, Duration.between(now, times.peekFirst().plus(WINDOW)).toSeconds());
            throw new SessionException(ErrorCode.RATE_LIMITED, Map.of("retryAfterSeconds", retry));
        }
        times.addLast(now);
    }
}
