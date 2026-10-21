package com.capybee.engine.session;

import com.capybee.engine.adapter.Randoms;
import com.capybee.engine.adapter.SequentialIdSource;
import com.capybee.engine.api.Command;
import com.capybee.engine.api.Engine;
import com.capybee.engine.generation.GenerationArtifact;
import com.capybee.engine.pack.DirectoryPackSource;
import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.Pack;
import com.capybee.engine.pack.PackId;
import com.capybee.engine.pack.PackRegistry;
import com.capybee.engine.pack.json.PackLoader;
import com.capybee.engine.port.StructuredGenerationRequest;
import com.capybee.engine.port.TextGenerationException;
import com.capybee.engine.port.TextGenerationResult;
import com.capybee.engine.port.TextGenerator;
import com.capybee.engine.session.adapter.InMemorySessionStore;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.capybee.engine.session.SessionTypes.*;
import static org.assertj.core.api.Assertions.assertThat;

class RunSessionPrefetchTest {
    private static final Path PACK_PATH = Path.of("..", "fixtures", "game-packs", "fixture-generated")
            .toAbsolutePath().normalize();
    private static final Instant START = Instant.parse("2026-10-19T10:00:00Z");

    @Test
    void enteringAnInFlightPrefetchJoinsItAndCountsOneProviderCall() throws Exception {
        CountDownLatch meetingEntered = new CountDownLatch(1);
        CountDownLatch releaseMeeting = new CountDownLatch(1);
        CountDownLatch insertEntered = new CountDownLatch(1);
        CountDownLatch releaseInsert = new CountDownLatch(1);
        AtomicReference<Instant> now = new AtomicReference<>(START);
        ConcurrentMap<String, AtomicInteger> calls = new ConcurrentHashMap<>();
        TextGenerator provider = request -> {
            String slot = request.slotKey();
            calls.computeIfAbsent(slot == null ? request.taskId() : slot, ignored -> new AtomicInteger())
                    .incrementAndGet();
            if ("meeting-slot".equals(slot)) {
                meetingEntered.countDown();
                await(releaseMeeting);
            }
            return new TextGenerationResult(answer(request), null, "test-provider",
                    TextGenerationResult.FinishReason.COMPLETE);
        };
        try (TrackingExecutor workers = new TrackingExecutor()) {
            Harness harness = harness(provider, workers, now, limits(10, 0, 0),
                store -> new GatedPrefetchStore(store, insertEntered, releaseInsert));
            UUID runId = start(harness);
            assertThat(meetingEntered.await(2, TimeUnit.SECONDS)).isTrue();

            UUID commandId = UUID.randomUUID();
            CommandOutcome accepted = harness.service.command(harness.owner, runId,
                    new CommandRequest(commandId, 0, new Command.Choose("explore")), Duration.ZERO);
            assertThat(accepted.outcome()).isEqualTo(Outcome.PROCESSING);
            Thread commandWorker = workers.awaitThread(2);
            awaitWaiting(commandWorker);
            assertThat(calls.get("meeting-slot")).hasValue(1);

            now.set(START.plusMillis(41));
            releaseMeeting.countDown();
            assertThat(insertEntered.await(2, TimeUnit.SECONDS)).isTrue();
            awaitCommand(harness.service, harness.owner, runId, commandId);

            Diagnostics diagnostics = harness.service.diagnostics(harness.owner, runId);
            assertThat(diagnostics.prefetch().prefetchCalls()).isEqualTo(1);
            assertThat(diagnostics.prefetch().hits()).isEqualTo(1);
            assertThat(diagnostics.recentCommands().getFirst().sceneSource()).isEqualTo(SceneSource.JOINED_PREFETCH);
            assertThat(diagnostics.recentCommands().getFirst().latencyMillis()).isEqualTo(41);
            assertThat(calls.get("meeting-slot")).hasValue(1);
            releaseInsert.countDown();
            awaitPrefetchComplete(harness.service, harness.owner, runId);
            assertThat(harness.store.load(runId)).isEmpty();
            assertThat(diagnostics.prefetch().wastedCalls()).isZero();
        } finally {
            releaseMeeting.countDown();
            releaseInsert.countDown();
        }
    }

    @Test
    void commandToAuthoredEndingDoesNotWaitForUnrelatedPrefetch() throws Exception {
        CountDownLatch meetingEntered = new CountDownLatch(1);
        CountDownLatch releaseMeeting = new CountDownLatch(1);
        AtomicReference<Instant> now = new AtomicReference<>(START);
        TextGenerator provider = request -> {
            if ("meeting-slot".equals(request.slotKey())) {
                meetingEntered.countDown();
                await(releaseMeeting);
            }
            return new TextGenerationResult(answer(request), null, "test-provider",
                    TextGenerationResult.FinishReason.COMPLETE);
        };
        try (TrackingExecutor workers = new TrackingExecutor()) {
                Harness harness = harness(provider, workers, now, limits(10, 0, 0));
            UUID runId = start(harness);
            assertThat(meetingEntered.await(2, TimeUnit.SECONDS)).isTrue();

            UUID commandId = UUID.randomUUID();
                try (var client = Executors.newSingleThreadExecutor()) {
                var response = client.submit(() -> harness.service.command(harness.owner, runId,
                    new CommandRequest(commandId, 0, new Command.Choose("rest")), Duration.ofSeconds(2)));
                assertThat(response.get(1, TimeUnit.SECONDS).outcome()).isEqualTo(Outcome.APPLIED);
                }
            Thread commandWorker = workers.awaitThread(2);
            awaitCommand(harness.service, harness.owner, runId, commandId);
                commandWorker.join(TimeUnit.SECONDS.toMillis(1));

            assertThat(commandWorker.isAlive()).isFalse();
            assertThat(harness.service.get(harness.owner, runId).status()).isEqualTo(RunStatus.ENDED);
            assertThat(releaseMeeting.getCount()).isEqualTo(1);
        } finally {
            releaseMeeting.countDown();
        }
    }

    @Test
    void failedPrefetchIsDiscardedAndDemandRetriesTheScene() throws Exception {
        AtomicInteger meetingCalls = new AtomicInteger();
        AtomicReference<Instant> now = new AtomicReference<>(START);
        TextGenerator provider = request -> {
            if ("meeting-slot".equals(request.slotKey()) && meetingCalls.incrementAndGet() == 1) {
                throw new TextGenerationException(TextGenerationException.Code.UNAVAILABLE);
            }
            return new TextGenerationResult(answer(request), null, "test-provider",
                    TextGenerationResult.FinishReason.COMPLETE);
        };
        try (TrackingExecutor workers = new TrackingExecutor()) {
            Harness harness = harness(provider, workers, now, limits(10, 0, 0));
            UUID runId = start(harness);
            awaitPrefetchComplete(harness.service, harness.owner, runId);

            CommandOutcome outcome = harness.service.command(harness.owner, runId,
                    new CommandRequest(UUID.randomUUID(), 0, new Command.Choose("explore")), Duration.ofSeconds(2));

            assertThat(outcome.outcome()).isEqualTo(Outcome.APPLIED);
            assertThat(meetingCalls).hasValue(2);
            assertThat(harness.store.load(runId)).isEmpty();
            Diagnostics diagnostics = harness.service.diagnostics(harness.owner, runId);
            assertThat(diagnostics.prefetch().prefetchCalls()).isEqualTo(1);
            assertThat(diagnostics.recentCommands().getFirst().sceneSource()).isEqualTo(SceneSource.INLINE_GENERATED);
        }
    }

    @Test
    void deletingRunDuringPrefetchDoesNotRecreateIt() throws Exception {
        CountDownLatch meetingEntered = new CountDownLatch(1);
        CountDownLatch releaseMeeting = new CountDownLatch(1);
        AtomicReference<Instant> now = new AtomicReference<>(START);
        TextGenerator provider = request -> {
            if ("meeting-slot".equals(request.slotKey())) {
                meetingEntered.countDown();
                await(releaseMeeting);
            }
            return new TextGenerationResult(answer(request), null, "test-provider",
                    TextGenerationResult.FinishReason.COMPLETE);
        };
        try (TrackingExecutor workers = new TrackingExecutor()) {
            Harness harness = harness(provider, workers, now, limits(10, 0, 0));
            UUID runId = start(harness);
            assertThat(meetingEntered.await(2, TimeUnit.SECONDS)).isTrue();

            harness.service.delete(harness.owner, runId);
            assertThat(harness.store.find(runId, harness.owner)).isEmpty();
            releaseMeeting.countDown();
            workers.awaitThread(1).join(TimeUnit.SECONDS.toMillis(2));

            assertThat(harness.store.find(runId, harness.owner)).isEmpty();
            assertThat(harness.store.load(runId)).isEmpty();
        } finally {
            releaseMeeting.countDown();
        }
    }

    @Test
    void reservesKeepPrefetchOutOfTheDemandBudget() throws Exception {
        AtomicReference<Instant> now = new AtomicReference<>(START);
        ConcurrentMap<String, AtomicInteger> calls = new ConcurrentHashMap<>();
        TextGenerator provider = request -> {
            calls.computeIfAbsent(request.slotKey() == null ? request.taskId() : request.slotKey(),
                    ignored -> new AtomicInteger()).incrementAndGet();
            return new TextGenerationResult(answer(request), null, "test-provider",
                    TextGenerationResult.FinishReason.COMPLETE);
        };
        try (TrackingExecutor workers = new TrackingExecutor()) {
            SessionLimits limits = new SessionLimits(Duration.ofSeconds(5), Duration.ofMinutes(2),
                    6, 10, 2, 10, true, 3, 1, 8, 100, 4);
            Harness harness = harness(provider, workers, now, limits);
            UUID runId = start(harness);
            awaitPrefetchComplete(harness.service, harness.owner, runId);

            assertThat(harness.service.diagnostics(harness.owner, runId).prefetch().prefetchCalls()).isZero();
            CommandOutcome outcome = harness.service.command(harness.owner, runId,
                    new CommandRequest(UUID.randomUUID(), 0, new Command.Choose("explore")), Duration.ofSeconds(2));

            assertThat(outcome.outcome()).isEqualTo(Outcome.APPLIED);
            assertThat(calls.get("meeting-slot")).hasValue(1);
            assertThat(harness.service.diagnostics(harness.owner, runId).generationCalls()).isEqualTo(5);
        }
    }

    private static Harness harness(TextGenerator provider, TrackingExecutor workers,
                                   AtomicReference<Instant> now, SessionLimits limits) {
        return harness(provider, workers, now, limits, null);
    }

    private static Harness harness(TextGenerator provider, TrackingExecutor workers,
                                   AtomicReference<Instant> now, SessionLimits limits,
                       java.util.function.Function<InMemorySessionStore,
                           SessionPorts.PrefetchStore> prefetchStore) {
        Pack pack = new PackLoader(Engine.sectionValidators())
                .load(new PackId("fixture-generated"), new DirectoryPackSource(PACK_PATH));
        InMemorySessionStore store = new InMemorySessionStore(Clock.fixed(START, ZoneOffset.UTC));
        OwnerRef owner = new OwnerRef("prefetch-test");
        SequentialIdSource ids = new SequentialIdSource("prefetch-test");
        SessionGenerators generators = new SessionGenerators(provider, store, limits);
        SessionPorts.PackCatalog catalog = new SessionPorts.PackCatalog() {
            @Override public List<Pack> all() { return List.of(pack); }
            @Override public java.util.Optional<Pack> find(String id) {
                return pack.manifest().id().value().equals(id) ? java.util.Optional.of(pack)
                        : java.util.Optional.empty();
            }
        };
        SessionPorts.EngineFactory factory = (ignoredOwner, runId, priority) -> {
            var generator = generators.forRun(new LedgerScope("profile:prefetch-test", limits.profileCallsPerDay()),
                    LocalDate.ofInstant(now.get(), ZoneOffset.UTC), runId, priority);
            return Engine.builder(new PackRegistry(List.of(pack)))
                    .clock(Clock.fixed(START, ZoneOffset.UTC))
                    .random(Randoms.seeded(17))
                    .ids(ids)
                    .textGenerator(generator).build();
        };
        RunSessionService service = new RunSessionService(catalog, store, store, store,
            prefetchStore == null ? store : prefetchStore.apply(store), factory,
                now::get, limits, store, workers, new byte[32], store,
                ignored -> new LedgerScope("profile:prefetch-test", limits.profileCallsPerDay()),
                generators.registry());
        return new Harness(service, store, owner, pack);
    }

    private static UUID start(Harness harness) {
        SessionTypes.StartAccepted accepted = harness.service.start(harness.owner,
                new StartRequest(UUID.randomUUID(), harness.pack.manifest().id(),
                        harness.pack.manifest().defaultLocale(), Map.of(), 17L, true), Duration.ofSeconds(2));
        return accepted.runId();
    }

    private static SessionLimits limits(int callsPerDay, int profileReserve, int runReserve) {
        return new SessionLimits(Duration.ofSeconds(5), Duration.ofMinutes(2), 20, callsPerDay, 2,
                callsPerDay, true, 3, 1, profileReserve, 0, runReserve);
    }

    private static String answer(StructuredGenerationRequest request) {
        if (request.slotKey() == null) {
            return switch (request.taskId()) {
                case "GEN_SCENARIO" -> "{\"title\":\"An Evening\",\"premise\":\"A lantern waits.\",\"setting\":\"Observatory\",\"beats\":[\"opening-slot\",\"meeting-slot\"]}";
                case "GEN_CAST" -> "{\"characters\":[]}";
                case "GEN_QUEST" -> "{\"objectives\":[]}";
                default -> "{}";
            };
        }
        if (request.slotKey().equals("opening-slot")) {
            return scene("A Quiet Start", "Opening generated.", "explore", "Look around", "rest", "Rest");
        }
        return scene("A New Room", "Meeting generated.", "continue", "Continue", "finish", "Finish");
    }

    private static String scene(String title, String narration, String firstId, String firstLabel,
                                String secondId, String secondLabel) {
        return "{\"title\":\"" + title + "\",\"narration\":\"" + narration
                + "\",\"dialogue\":[],\"choices\":[{\"id\":\"" + firstId
                + "\",\"label\":\"" + firstLabel + "\"},{\"id\":\"" + secondId
                + "\",\"label\":\"" + secondLabel + "\"}]}";
    }

    private static void awaitCommand(RunSessionService service, OwnerRef owner, UUID runId, UUID commandId) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            CommandStatus status = service.commandStatus(owner, runId, commandId);
            if (!status.processing()) return;
            Thread.onSpinWait();
        }
        throw new AssertionError("command did not finish");
    }

    private static void awaitPrefetchComplete(RunSessionService service, OwnerRef owner, UUID runId) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (service.diagnostics(owner, runId).prefetch().inFlight().isEmpty()) return;
            Thread.onSpinWait();
        }
        throw new AssertionError("prefetch did not complete");
    }

    private static void awaitWaiting(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (thread.getState() == Thread.State.WAITING || thread.getState() == Thread.State.TIMED_WAITING) return;
            Thread.onSpinWait();
        }
        throw new AssertionError("command did not wait on the in-flight generation");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) throw new IllegalStateException("test gate timed out");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("test gate interrupted", interrupted);
        }
    }

    private record Harness(RunSessionService service, InMemorySessionStore store, OwnerRef owner, Pack pack) {}

    private static final class GatedPrefetchStore implements SessionPorts.PrefetchStore {
        private final InMemorySessionStore store;
        private final CountDownLatch entered;
        private final CountDownLatch release;

        private GatedPrefetchStore(InMemorySessionStore store, CountDownLatch entered, CountDownLatch release) {
            this.store = store;
            this.entered = entered;
            this.release = release;
        }

        @Override public List<PrefetchedArtifact> load(UUID runId) { return store.load(runId); }
        @Override public boolean insertIfAbsent(UUID runId, String sceneId, long revision,
                                                GenerationArtifact artifact) {
            entered.countDown();
            await(release);
            return store.insertIfAbsent(runId, sceneId, revision, artifact);
        }
        @Override public void deleteForScenes(UUID runId, Collection<String> sceneIds) {
            store.deleteForScenes(runId, sceneIds);
        }
    }

    private static final class TrackingExecutor implements Executor, AutoCloseable {
        private final ConcurrentLinkedQueue<Thread> threads = new ConcurrentLinkedQueue<>();
        private final AtomicInteger sequence = new AtomicInteger();

        @Override public void execute(Runnable task) {
            Thread thread = new Thread(task, "session-worker-" + sequence.incrementAndGet());
            threads.add(thread);
            thread.start();
        }

        Thread awaitThread(int index) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (System.nanoTime() < deadline) {
                Thread[] current = threads.toArray(Thread[]::new);
                if (current.length > index) return current[index];
                Thread.onSpinWait();
            }
            throw new AssertionError("expected session worker was not started");
        }

        @Override public void close() throws InterruptedException {
            for (Thread thread : threads) thread.join(TimeUnit.SECONDS.toMillis(2));
        }
    }
}
