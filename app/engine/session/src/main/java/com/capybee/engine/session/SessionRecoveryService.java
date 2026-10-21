package com.capybee.engine.session;

import com.capybee.engine.session.SessionPorts.RecoveryStore;
import com.capybee.engine.session.SessionTypes.RecoverableRun;
import com.capybee.engine.session.SessionTypes.RecoveryState;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Provider-neutral, idempotent reconciliation of durable work left by a stopped process. */
public final class SessionRecoveryService {
    private final RecoveryStore store;
    private final SessionPorts.SessionClock clock;
    private final AtomicBoolean running = new AtomicBoolean();

    public SessionRecoveryService(RecoveryStore store, SessionPorts.SessionClock clock) {
        this.store = java.util.Objects.requireNonNull(store);
        this.clock = java.util.Objects.requireNonNull(clock);
    }

    public List<RecoverableRun> recover() {
        if (!running.compareAndSet(false, true)) throw new SessionException(SessionTypes.ErrorCode.RECOVERY_IN_PROGRESS);
        try {
            Instant now = clock.now();
            List<RecoverableRun> result = new ArrayList<>();
            for (RecoverableRun run : store.scanRecoverable()) {
                if (run.state() == RecoveryState.INTERRUPTED_COMMAND && store.recoverInterrupted(run.runId(), now)) {
                    result.add(new RecoverableRun(run.runId(), run.revision(), RecoveryState.RECOVERED,
                            "COMMAND_INTERRUPTED"));
                } else result.add(run);
            }
            return List.copyOf(result);
        } finally {
            running.set(false);
        }
    }
}
