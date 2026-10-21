package com.capybee.engine.session;

import com.capybee.engine.port.TextGenerator;
import com.capybee.engine.session.SessionPorts.GenerationLedger;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import static com.capybee.engine.session.SessionTypes.LedgerScope;

public final class SessionGenerators {
    private final TextGenerator provider;
    private final GenerationLedger ledger;
    private final SessionLimits limits;
    private final SingleFlightRegistry registry = new SingleFlightRegistry();
    private final LedgerScope global;

    public SessionGenerators(TextGenerator provider, GenerationLedger ledger, SessionLimits limits) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.global = new LedgerScope("global", limits.globalCallsPerDay());
    }

    public TextGenerator forRun(LedgerScope profile, LocalDate day, UUID runId,
                                MeteredTextGenerator.Priority priority) {
        TextGenerator metered = new MeteredTextGenerator(provider, ledger, profile, global, day, runId,
                limits.runCalls(), priority, limits.prefetchProfileReserve(), limits.prefetchGlobalReserve(),
                limits.prefetchRunReserve());
        return new SingleFlightTextGenerator(metered, registry, runId);
    }

    public SingleFlightRegistry registry() {
        return registry;
    }
}
