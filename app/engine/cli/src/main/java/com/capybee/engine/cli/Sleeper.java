package com.capybee.engine.cli;

import java.time.Duration;

@FunctionalInterface
interface Sleeper {
    void sleep(Duration duration);

    static Sleeper system() {
        return duration -> {
            try {
                Thread.sleep(duration.toMillis());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        };
    }
}
