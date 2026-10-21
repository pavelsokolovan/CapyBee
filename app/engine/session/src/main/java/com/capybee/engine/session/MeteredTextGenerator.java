package com.capybee.engine.session;

import com.capybee.engine.port.StructuredGenerationRequest;
import com.capybee.engine.port.TextGenerationException;
import com.capybee.engine.port.TextGenerationResult;
import com.capybee.engine.port.TextGenerator;
import com.capybee.engine.session.SessionPorts.GenerationLedger;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import static com.capybee.engine.session.SessionTypes.LedgerScope;

public final class MeteredTextGenerator implements TextGenerator {
    public enum Priority { DEMAND, PREFETCH }

    private final TextGenerator delegate;
    private final GenerationLedger ledger;
    private final LedgerScope profile;
    private final LedgerScope global;
    private final LocalDate day;
    private final UUID runId;
    private final int runLimit;
    private final Priority priority;
    private final int profileReserve;
    private final int globalReserve;
    private final int runReserve;

    public MeteredTextGenerator(TextGenerator delegate, GenerationLedger ledger,
                                LedgerScope profile, LedgerScope global, LocalDate day,
                                UUID runId, int runLimit, Priority priority,
                                int profileReserve, int globalReserve, int runReserve) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.global = Objects.requireNonNull(global, "global");
        this.day = Objects.requireNonNull(day, "day");
        this.runId = Objects.requireNonNull(runId, "runId");
        this.priority = Objects.requireNonNull(priority, "priority");
        if (runLimit < 1 || profileReserve < 0 || globalReserve < 0 || runReserve < 0) {
            throw new IllegalArgumentException("invalid generation budget");
        }
        this.runLimit = runLimit;
        this.profileReserve = profileReserve;
        this.globalReserve = globalReserve;
        this.runReserve = runReserve;
    }

    @Override
    public TextGenerationResult generate(StructuredGenerationRequest request) {
        if (!ledger.tryReserveCall(profile, global, day, runId, runLimit, priority,
                profileReserve, globalReserve, runReserve)) {
            throw new TextGenerationException(TextGenerationException.Code.RATE_LIMITED);
        }
        TextGenerationResult result = delegate.generate(request);
        if (result.usage() != null) {
            ledger.recordUsage(profile, global, day, runId, result.usage(), priority);
        }
        return result;
    }
}
