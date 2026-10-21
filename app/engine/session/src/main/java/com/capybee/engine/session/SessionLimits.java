package com.capybee.engine.session;

import java.time.Duration;

public record SessionLimits(Duration leaseDuration, Duration startTtl, int runCalls,
                            int globalCallsPerDay, int maxConcurrentGeneration,
                            int profileCallsPerDay, boolean prefetchEnabled,
                            int prefetchMaxFanout, int prefetchMaxConcurrent,
                            int prefetchProfileReserve, int prefetchGlobalReserve,
                            int prefetchRunReserve) {
    public SessionLimits {
        if (leaseDuration == null || leaseDuration.isNegative() || leaseDuration.isZero()
                || startTtl == null || startTtl.isNegative() || startTtl.isZero()
                || runCalls < 1 || globalCallsPerDay < 0 || maxConcurrentGeneration < 1
                || profileCallsPerDay < 0 || prefetchMaxFanout < 1 || prefetchMaxFanout > 3
                || prefetchMaxConcurrent < 1 || prefetchProfileReserve < 0
                || prefetchGlobalReserve < 0 || prefetchRunReserve < 0) {
            throw new IllegalArgumentException("invalid session limits");
        }
    }

    /** Compatibility constructor for hosts with explicit profile limits but no prefetch tuning. */
    public SessionLimits(Duration leaseDuration, Duration startTtl, int runCalls,
                         int globalCallsPerDay, int maxConcurrentGeneration,
                         int profileCallsPerDay, boolean prefetchEnabled) {
        this(leaseDuration, startTtl, runCalls, globalCallsPerDay, maxConcurrentGeneration,
                profileCallsPerDay, prefetchEnabled, 3, 1, 8, 100, 4);
    }

    /** Compatibility constructor for hosts written before per-profile limits existed. */
    public SessionLimits(Duration leaseDuration, Duration startTtl, int runCalls,
                         int globalCallsPerDay, int maxConcurrentGeneration) {
        this(leaseDuration, startTtl, runCalls, globalCallsPerDay, maxConcurrentGeneration,
                40, true, 3, 1, 8, 100, 4);
    }

    public static SessionLimits defaults() {
        return new SessionLimits(Duration.ofSeconds(30), Duration.ofMinutes(10), 24, 1500, 2,
                40, true, 3, 1, 8, 100, 4);
    }
}
