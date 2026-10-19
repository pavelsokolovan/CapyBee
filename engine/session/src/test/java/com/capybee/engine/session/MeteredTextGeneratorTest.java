package com.capybee.engine.session;

import com.capybee.engine.port.StructuredGenerationRequest;
import com.capybee.engine.port.TextGenerationException;
import com.capybee.engine.port.TextGenerationResult;
import com.capybee.engine.session.adapter.InMemorySessionStore;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.capybee.engine.session.SessionTypes.LedgerScope;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MeteredTextGeneratorTest {
    @Test
    void accountsSuccessfulCallsAndRefusesAtTheDailyLimit() {
        InMemorySessionStore ledger = new InMemorySessionStore();
        AtomicInteger providerCalls = new AtomicInteger();
        var generator = new MeteredTextGenerator(request -> {
            providerCalls.incrementAndGet();
            return result();
        }, ledger, new LedgerScope("profile:one", 1), new LedgerScope("global", 10),
                LocalDate.of(2026, 10, 19), UUID.randomUUID(), 5,
                MeteredTextGenerator.Priority.DEMAND, 0, 0, 0);

        assertThat(generator.generate(request()).outputJson()).isEqualTo("{}");
        assertThatThrownBy(() -> generator.generate(request()))
                .isInstanceOf(TextGenerationException.class)
                .extracting(error -> ((TextGenerationException) error).code())
                .isEqualTo(TextGenerationException.Code.RATE_LIMITED);
        assertThat(providerCalls).hasValue(1);
    }

    @Test
    void prefetchLeavesConfiguredCallsForDemand() {
        InMemorySessionStore ledger = new InMemorySessionStore();
        LedgerScope profile = new LedgerScope("profile:two", 2);
        LedgerScope global = new LedgerScope("global", 10);
        UUID runId = UUID.randomUUID();
        var prefetch = new MeteredTextGenerator(request -> result(), ledger, profile, global,
                LocalDate.of(2026, 10, 19), runId, 3,
                MeteredTextGenerator.Priority.PREFETCH, 1, 1, 1);
        var demand = new MeteredTextGenerator(request -> result(), ledger, profile, global,
                LocalDate.of(2026, 10, 19), runId, 3,
                MeteredTextGenerator.Priority.DEMAND, 1, 1, 1);

        assertThat(prefetch.generate(request())).isNotNull();
        assertThatThrownBy(() -> prefetch.generate(request()))
                .isInstanceOf(TextGenerationException.class);
        assertThat(demand.generate(request())).isNotNull();
    }

    private static StructuredGenerationRequest request() {
        return new StructuredGenerationRequest("request", "scene", "policy", "instructions",
                "{}", "{}", 128, StructuredGenerationRequest.CostClass.LIGHT);
    }

    private static TextGenerationResult result() {
        return new TextGenerationResult("{}", new TextGenerationResult.TokenUsage(2, 3, 0, 0),
                "fake", TextGenerationResult.FinishReason.COMPLETE);
    }
}
