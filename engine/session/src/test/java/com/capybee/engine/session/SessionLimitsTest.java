package com.capybee.engine.session;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionLimitsTest {
    @Test
    void validatesPrefetchControlsButAllowsReservesAboveCaps() {
        assertThatThrownBy(() -> limits(0, 1, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> limits(4, 1, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> limits(1, 0, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> limits(1, 1, -1, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> limits(1, 1, 0, -1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> limits(1, 1, 0, 0, -1))
                .isInstanceOf(IllegalArgumentException.class);

        SessionLimits aboveCaps = new SessionLimits(Duration.ofSeconds(1), Duration.ofSeconds(1),
                2, 3, 1, 1, true, 1, 1, 8, 100, 4);

        assertThat(aboveCaps.prefetchProfileReserve()).isEqualTo(8);
        assertThat(aboveCaps.prefetchGlobalReserve()).isEqualTo(100);
        assertThat(aboveCaps.prefetchRunReserve()).isEqualTo(4);
    }

    @Test
    void oldConstructorsUsePrefetchDefaults() {
        SessionLimits fiveArgument = new SessionLimits(Duration.ofSeconds(1), Duration.ofSeconds(2),
                24, 1500, 2);
        SessionLimits sevenArgument = new SessionLimits(Duration.ofSeconds(1), Duration.ofSeconds(2),
                24, 1500, 2, 40, false);

        assertThat(fiveArgument.prefetchEnabled()).isTrue();
        assertThat(sevenArgument.prefetchEnabled()).isFalse();
        for (SessionLimits limits : new SessionLimits[]{fiveArgument, sevenArgument, SessionLimits.defaults()}) {
            assertThat(limits.prefetchMaxFanout()).isEqualTo(3);
            assertThat(limits.prefetchMaxConcurrent()).isEqualTo(1);
            assertThat(limits.prefetchProfileReserve()).isEqualTo(8);
            assertThat(limits.prefetchGlobalReserve()).isEqualTo(100);
            assertThat(limits.prefetchRunReserve()).isEqualTo(4);
        }
    }

    private SessionLimits limits(int fanout, int concurrent, int profileReserve,
                                 int globalReserve, int runReserve) {
        return new SessionLimits(Duration.ofSeconds(1), Duration.ofSeconds(1), 1, 1, 1,
                1, true, fanout, concurrent, profileReserve, globalReserve, runReserve);
    }
}