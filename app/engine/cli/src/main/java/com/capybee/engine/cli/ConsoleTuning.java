package com.capybee.engine.cli;

import java.time.Duration;

record ConsoleTuning(Duration syncWait, Duration minPoll, Duration maxPoll, Duration maxWait,
                     Sleeper sleeper) {
    ConsoleTuning {
        if (syncWait == null || minPoll == null || maxPoll == null || maxWait == null || sleeper == null
                || syncWait.isNegative() || minPoll.isNegative() || maxPoll.compareTo(minPoll) < 0
                || maxWait.isNegative()) {
            throw new IllegalArgumentException("invalid console tuning");
        }
    }

    static ConsoleTuning defaults() {
        return new ConsoleTuning(Duration.ofSeconds(8), Duration.ofMillis(250), Duration.ofSeconds(5),
                Duration.ofMinutes(3), Sleeper.system());
    }

    Duration pollDelay(long retryAfterMillis) {
        Duration requested = Duration.ofMillis(Math.max(0, retryAfterMillis));
        return requested.compareTo(minPoll) < 0 ? minPoll
                : requested.compareTo(maxPoll) > 0 ? maxPoll : requested;
    }
}
