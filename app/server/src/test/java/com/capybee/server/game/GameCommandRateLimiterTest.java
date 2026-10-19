package com.capybee.server.game;

import com.capybee.engine.session.SessionException;
import com.capybee.engine.session.SessionTypes.ErrorCode;
import com.capybee.engine.session.SessionTypes.OwnerRef;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameCommandRateLimiterTest {
    @Test
    void limitsCommandsPerOwnerAndAllowsCommandsAfterTheWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-19T10:00:00Z"));
        GameCommandRateLimiter limiter = new GameCommandRateLimiter(clock, 2);
        OwnerRef owner = new OwnerRef("owner-1");

        limiter.check(owner);
        limiter.check(owner);
        assertThatThrownBy(() -> limiter.check(owner))
                .isInstanceOf(SessionException.class)
                .satisfies(failure -> {
                    SessionException sessionException = (SessionException) failure;
                    assertThat(sessionException.code()).isEqualTo(ErrorCode.RATE_LIMITED);
                    assertThat(sessionException.details()).containsEntry("retryAfterSeconds", 60L);
                });

        clock.advance(Duration.ofMinutes(1));
        limiter.check(owner);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
