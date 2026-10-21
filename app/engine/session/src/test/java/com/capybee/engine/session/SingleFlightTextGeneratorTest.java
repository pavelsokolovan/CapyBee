package com.capybee.engine.session;

import com.capybee.engine.port.StructuredGenerationRequest;
import com.capybee.engine.port.TextGenerationResult;
import com.capybee.engine.port.TextGenerator;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SingleFlightTextGeneratorTest {
    private static final TextGenerationResult RESULT = new TextGenerationResult("{}", null, "test",
            TextGenerationResult.FinishReason.COMPLETE);

    @Test
    void joinsOneInFlightCallAndMarksTheFollowerThread() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        TextGenerator provider = request -> {
            calls.incrementAndGet();
            entered.countDown();
            await(release);
            return RESULT;
        };
        SingleFlightRegistry registry = new SingleFlightRegistry();
        SingleFlightTextGenerator generator = new SingleFlightTextGenerator(provider, registry, UUID.randomUUID());
        try (var workers = Executors.newFixedThreadPool(2)) {
            var leader = workers.submit(() -> generator.generate(request("scene-a")));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            AtomicReference<TextGenerationResult> followerResult = new AtomicReference<>();
            AtomicReference<Thread> followerThread = new AtomicReference<>();
            var follower = workers.submit(() -> {
                followerThread.set(Thread.currentThread());
                followerResult.set(generator.generate(request("scene-a")));
                return registry.consumeJoined();
            });
            awaitWaiting(followerThread);
            release.countDown();

            assertThat(leader.get(2, TimeUnit.SECONDS)).isSameAs(RESULT);
            assertThat(follower.get(2, TimeUnit.SECONDS)).isTrue();
            assertThat(followerResult.get()).isSameAs(RESULT);
            assertThat(calls).hasValue(1);
        }
    }

    @Test
    void retriesOnceAfterLeaderFailureAndPassesThroughUnkeyedRequests() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        SingleFlightRegistry registry = new SingleFlightRegistry();
        SingleFlightTextGenerator generator = new SingleFlightTextGenerator(request -> {
            calls.incrementAndGet();
            return RESULT;
        }, registry, UUID.randomUUID());

        assertThat(generator.generate(request(null))).isSameAs(RESULT);
        assertThat(generator.generate(request("scene-a"))).isSameAs(RESULT);
        assertThat(generator.generate(request("scene-a"))).isSameAs(RESULT);
        assertThat(calls).hasValue(3);
        assertThat(registry.consumeJoined()).isFalse();

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger failingCalls = new AtomicInteger();
        SingleFlightRegistry failingRegistry = new SingleFlightRegistry();
        SingleFlightTextGenerator failing = new SingleFlightTextGenerator(request -> {
            if (failingCalls.incrementAndGet() == 1) {
                entered.countDown();
                await(release);
                throw new IllegalStateException("provider failed");
            }
            return RESULT;
        }, failingRegistry, UUID.randomUUID());
        try (var workers = Executors.newFixedThreadPool(2)) {
            var leader = workers.submit(() -> failing.generate(request("scene-b")));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            AtomicReference<Thread> followerThread = new AtomicReference<>();
            var follower = workers.submit(() -> {
                followerThread.set(Thread.currentThread());
                return failing.generate(request("scene-b"));
            });
            awaitWaiting(followerThread);
            release.countDown();
            assertThatThrownBy(() -> leader.get(2, TimeUnit.SECONDS)).hasCauseInstanceOf(IllegalStateException.class);
            assertThat(follower.get(2, TimeUnit.SECONDS)).isSameAs(RESULT);
            assertThat(failingCalls).hasValue(2);
        }
    }

    @Test
    void differentKeysAndRunsDoNotJoin() {
        AtomicInteger calls = new AtomicInteger();
        TextGenerator provider = request -> {
            calls.incrementAndGet();
            return RESULT;
        };
        SingleFlightRegistry registry = new SingleFlightRegistry();
        SingleFlightTextGenerator firstRun = new SingleFlightTextGenerator(provider, registry, UUID.randomUUID());
        SingleFlightTextGenerator secondRun = new SingleFlightTextGenerator(provider, registry, UUID.randomUUID());

        firstRun.generate(request("scene-a"));
        firstRun.generate(request("scene-b"));
        secondRun.generate(request("scene-a"));

        assertThat(calls).hasValue(3);
    }

    private static StructuredGenerationRequest request(String slotKey) {
        return new StructuredGenerationRequest("request", "GEN_SCENE", "policy", "instructions", "{}",
                "{}", 100, StructuredGenerationRequest.CostClass.STANDARD, slotKey);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) throw new IllegalStateException("test gate timed out");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("test gate interrupted", interrupted);
        }
    }

    private static void awaitWaiting(AtomicReference<Thread> threadReference) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            Thread thread = threadReference.get();
                if (thread != null && (thread.getState() == Thread.State.WAITING
                    || thread.getState() == Thread.State.TIMED_WAITING)) return;
            Thread.onSpinWait();
        }
        throw new AssertionError("follower did not join the in-flight call");
    }
}
