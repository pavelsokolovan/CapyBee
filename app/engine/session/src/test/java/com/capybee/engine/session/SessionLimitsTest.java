package com.capybee.engine.session;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.capybee.engine.pack.LocaleTag;

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

    @Test
    void sourceNeutralValueTypesExposeSafeDefaultsAndDynamicOutcomes() {
        assertThat(SessionTypes.RunQuery.visible())
                .isEqualTo(new SessionTypes.RunQuery(false, false));
        assertThat(SessionTypes.PurgeConfirmation.denied())
                .isEqualTo(new SessionTypes.PurgeConfirmation(false, false, Duration.ofDays(30)));
        var summary = new SessionTypes.RunSummary(UUID.randomUUID(), SessionTypes.RunStatus.ACTIVE,
                0, null, null, new LocaleTag("en"), null, null, null, Instant.EPOCH);
        assertThat(summary.dynamicGame()).isTrue();

        assertThat(new DynamicStartCoordinator.Started(summary).run()).isSameAs(summary);
        assertThat(new DynamicStartCoordinator.NeedsInput("INPUT_BLOCKED").code()).isEqualTo("INPUT_BLOCKED");
        assertThat(new DynamicStartCoordinator.FallbackOffered("FALLBACK").code()).isEqualTo("FALLBACK");
        assertThat(new DynamicStartCoordinator.Cancelled("CANCELLED").code()).isEqualTo("CANCELLED");
    }

    private SessionLimits limits(int fanout, int concurrent, int profileReserve,
                                 int globalReserve, int runReserve) {
        return new SessionLimits(Duration.ofSeconds(1), Duration.ofSeconds(1), 1, 1, 1,
                1, true, fanout, concurrent, profileReserve, globalReserve, runReserve);
    }
}
