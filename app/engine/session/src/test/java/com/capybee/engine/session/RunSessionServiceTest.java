package com.capybee.engine.session;

import com.capybee.engine.api.Command;
import com.capybee.engine.api.EndReason;
import com.capybee.engine.api.Engine;
import com.capybee.engine.api.Event;
import com.capybee.engine.api.Issue;
import com.capybee.engine.api.PackRef;
import com.capybee.engine.api.ResumeResult;
import com.capybee.engine.api.Run;
import com.capybee.engine.api.RunSnapshot;
import com.capybee.engine.api.RunState;
import com.capybee.engine.api.StartResult;
import com.capybee.engine.api.StepResult;
import com.capybee.engine.api.View;
import com.capybee.engine.pack.ContentPolicy;
import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.Pack;
import com.capybee.engine.pack.PackFeature;
import com.capybee.engine.pack.PackId;
import com.capybee.engine.pack.PackManifest;
import com.capybee.engine.pack.PackVersion;
import com.capybee.engine.session.adapter.InMemorySessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static com.capybee.engine.session.SessionTypes.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RunSessionServiceTest {
    private static final PackId PACK_ID = new PackId("test-pack");
    private static final LocaleTag LOCALE = new LocaleTag("en");
    private static final PackVersion PACK_VERSION = new PackVersion(1, 0, 0);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T10:00:00Z"), ZoneOffset.UTC);
    private final OwnerRef owner = new OwnerRef("profile-1");
    private final InMemorySessionStore store = new InMemorySessionStore(CLOCK);
    private RunSessionService service;

    @BeforeEach
    void setUp() {
        Pack pack = pack();
        service = new RunSessionService(new TestPackCatalog(pack), store, store, store, store,
                (ignoredOwner, runId, priority) -> new TestEngine(runId, pack), CLOCK::instant,
                SessionLimits.defaults(), store, DIRECT, new byte[32]);
    }

    @Test
    void startsAndAppliesCommandExactlyOnceOnReplay() {
        UUID runId = UUID.randomUUID();
        StartAccepted started = service.start(owner,
                new StartRequest(runId, PACK_ID, LOCALE, Map.of("mood", "heavy"), 7L, false));

        assertThat(started.status()).isEqualTo(RunStatus.ACTIVE);
        CommandRequest request = new CommandRequest(UUID.randomUUID(), 0, new Command.Advance());
        CommandOutcome applied = service.command(owner, runId, request, Duration.ofSeconds(1));
        CommandOutcome replay = service.command(owner, runId, request, Duration.ofSeconds(1));

        assertThat(applied.outcome()).isEqualTo(Outcome.APPLIED);
        assertThat(applied.revision()).isEqualTo(1);
        assertThat(replay.outcome()).isEqualTo(Outcome.REPLAYED);
        assertThat(replay.revision()).isEqualTo(1);
        assertThat(service.get(owner, runId).revision()).isEqualTo(1);
    }

    @Test
    void rejectsReusedIdsAndStaleRevisions() {
        UUID runId = UUID.randomUUID();
        service.start(owner, new StartRequest(runId, PACK_ID, LOCALE, Map.of(), null, false));
        UUID commandId = UUID.randomUUID();
        service.command(owner, runId, new CommandRequest(commandId, 0, new Command.Advance()),
                Duration.ofSeconds(1));

        assertThatThrownBy(() -> service.command(owner, runId,
                new CommandRequest(commandId, 0, new Command.Choose("different")), Duration.ZERO))
                .isInstanceOf(SessionException.class)
                .extracting(error -> ((SessionException) error).code())
                .isEqualTo(ErrorCode.COMMAND_ID_REUSED);
        assertThatThrownBy(() -> service.command(owner, runId,
                new CommandRequest(UUID.randomUUID(), 0, new Command.Advance()), Duration.ZERO))
                .isInstanceOf(SessionException.class)
                .extracting(error -> ((SessionException) error).code())
                .isEqualTo(ErrorCode.REVISION_CONFLICT);
    }

    @Test
    void scopesRunAccessAndAllowsNewRunAfterDelete() {
        UUID runId = UUID.randomUUID();
        service.start(owner, new StartRequest(runId, PACK_ID, LOCALE, Map.of(), null, false));

        assertThatThrownBy(() -> service.get(new OwnerRef("other-profile"), runId))
                .isInstanceOf(SessionException.class)
                .extracting(error -> ((SessionException) error).code())
                .isEqualTo(ErrorCode.RUN_NOT_FOUND);
        assertThatThrownBy(() -> service.start(owner,
                new StartRequest(UUID.randomUUID(), PACK_ID, LOCALE, Map.of(), null, false)))
                .isInstanceOf(SessionException.class)
                .extracting(error -> ((SessionException) error).code())
                .isEqualTo(ErrorCode.RUN_ALREADY_ACTIVE);

        service.delete(owner, runId);
        assertThat(service.start(owner,
                new StartRequest(UUID.randomUUID(), PACK_ID, LOCALE, Map.of(), null, false)).status())
                .isEqualTo(RunStatus.ACTIVE);
    }

    @Test
    void rejectedCommandDoesNotAdvanceAndAbandonEndsTheRun() {
        UUID runId = UUID.randomUUID();
        service.start(owner, new StartRequest(runId, PACK_ID, LOCALE, Map.of(), null, false));
        CommandOutcome rejected = service.command(owner, runId,
                new CommandRequest(UUID.randomUUID(), 0, new Command.SubmitText("reject")),
                Duration.ofSeconds(1));

        assertThat(rejected.outcome()).isEqualTo(Outcome.REJECTED);
        assertThat(rejected.revision()).isZero();
        assertThat(service.get(owner, runId).revision()).isZero();
        CommandOutcome ended = service.command(owner, runId,
                new CommandRequest(UUID.randomUUID(), 0, new Command.Abandon()), Duration.ofSeconds(1));
        assertThat(ended.status()).isEqualTo(RunStatus.ENDED);
        assertThatThrownBy(() -> service.command(owner, runId,
                new CommandRequest(UUID.randomUUID(), 1, new Command.Advance()), Duration.ZERO))
                .isInstanceOf(SessionException.class)
                .extracting(error -> ((SessionException) error).code())
                .isEqualTo(ErrorCode.RUN_NOT_ACTIVE);
    }

    @Test
    void validatesRequestsAndListsPackMetadata() {
        assertThatThrownBy(() -> service.start(owner, null))
                .isInstanceOf(SessionException.class)
                .extracting(error -> ((SessionException) error).code())
                .isEqualTo(ErrorCode.REQUEST_INVALID);
        assertThat(service.packs(java.util.Locale.ENGLISH)).singleElement()
                .satisfies(info -> {
                    assertThat(info.id()).isEqualTo(PACK_ID.value());
                    assertThat(info.version()).isEqualTo(PACK_VERSION.toString());
                    assertThat(info.locales()).containsExactly("en");
                });
        assertThatThrownBy(() -> service.get(owner, UUID.randomUUID()))
                .isInstanceOf(SessionException.class)
                .extracting(error -> ((SessionException) error).code())
                .isEqualTo(ErrorCode.RUN_NOT_FOUND);
    }

    @Test
    void reportsCompletedCommandStatusAndReadsRunEvents() {
        UUID runId = UUID.randomUUID();
        service.start(owner, new StartRequest(runId, PACK_ID, LOCALE, Map.of(), null, false));
        UUID commandId = UUID.randomUUID();
        service.command(owner, runId,
                new CommandRequest(commandId, 0, new Command.Advance()), Duration.ofSeconds(1));

        CommandStatus status = service.commandStatus(owner, runId, commandId);

        assertThat(status.processing()).isFalse();
        assertThat(status.outcome().outcome()).isEqualTo(Outcome.REPLAYED);
        assertThat(status.retryAfterMs()).isZero();
        assertThat(service.events(owner, runId, 0, 10)).isEmpty();
        assertThatThrownBy(() -> service.events(owner, runId, -1, 10))
                .isInstanceOf(SessionException.class)
                .extracting(error -> ((SessionException) error).code())
                .isEqualTo(ErrorCode.REQUEST_INVALID);
        assertThatThrownBy(() -> service.events(owner, runId, 0, 101))
                .isInstanceOf(SessionException.class)
                .extracting(error -> ((SessionException) error).code())
                .isEqualTo(ErrorCode.REQUEST_INVALID);
    }

    @Test
    void reportsUnknownCommandStatusAsNotFound() {
        UUID runId = UUID.randomUUID();
        service.start(owner, new StartRequest(runId, PACK_ID, LOCALE, Map.of(), null, false));

        assertThatThrownBy(() -> service.commandStatus(owner, runId, UUID.randomUUID()))
                .isInstanceOf(SessionException.class)
                .extracting(error -> ((SessionException) error).code())
                .isEqualTo(ErrorCode.RUN_NOT_FOUND);
    }

    @Test
    void startCanReturnPreparingAndCompleteWhenTheWorkerRuns() {
        Pack pack = pack();
        InMemorySessionStore pendingStore = new InMemorySessionStore(CLOCK);
        java.util.ArrayDeque<Runnable> jobs = new java.util.ArrayDeque<>();
        RunSessionService pendingService = new RunSessionService(new TestPackCatalog(pack), pendingStore,
                pendingStore, pendingStore, pendingStore,
                (ignoredOwner, runId, priority) -> new TestEngine(runId, pack), CLOCK::instant,
                SessionLimits.defaults(), pendingStore, jobs::add, new byte[32]);
        UUID runId = UUID.randomUUID();

        StartAccepted accepted = pendingService.start(owner,
                new StartRequest(runId, PACK_ID, LOCALE, Map.of(), null, false), Duration.ZERO);
        assertThat(accepted.status()).isEqualTo(RunStatus.PREPARING);

        jobs.removeFirst().run();
        assertThat(pendingService.get(owner, runId).status()).isEqualTo(RunStatus.ACTIVE);
    }

    @Test
    void unexpectedStartFailureIsDurableAndIsNotScheduledAgain() {
        Pack pack = pack();
        InMemorySessionStore failedStore = new InMemorySessionStore(CLOCK);
        AtomicInteger attempts = new AtomicInteger();
        RunSessionService failedService = new RunSessionService(new TestPackCatalog(pack), failedStore,
                failedStore, failedStore, failedStore,
                (ignoredOwner, runId, priority) -> {
                    attempts.incrementAndGet();
                    throw new IllegalStateException("synthetic start failure");
                }, CLOCK::instant, SessionLimits.defaults(), failedStore, DIRECT, new byte[32]);
        UUID runId = UUID.randomUUID();

        assertThatThrownBy(() -> failedService.start(owner,
                new StartRequest(runId, PACK_ID, LOCALE, Map.of(), null, true)))
                .isInstanceOf(SessionException.class)
                .extracting(error -> ((SessionException) error).code())
                .isEqualTo(ErrorCode.START_FAILED);
        assertThat(failedStore.find(runId, owner).orElseThrow().status()).isEqualTo(RunStatus.FAILED);
        assertThat(failedStore.find(runId, owner).orElseThrow().failureCode()).isEqualTo("START_FAILED");
        assertThat(failedService.get(owner, runId).status()).isEqualTo(RunStatus.FAILED);
        assertThat(attempts).hasValue(1);
    }

        @Test
        void diagnosticsUsesStoredCountsAndNeverResumesTheSnapshot() {
                AtomicInteger resumes = new AtomicInteger();
                Pack pack = pack();
                InMemorySessionStore diagnosticsStore = new InMemorySessionStore(CLOCK);
                SessionLimits limits = new SessionLimits(Duration.ofSeconds(30), Duration.ofMinutes(10),
                                24, 1500, 2, 40, false);
                Engine noResumeEngine = new Engine() {
                        private final TestEngine engine = new TestEngine(UUID.randomUUID(), pack);
                        @Override public StartResult start(com.capybee.engine.api.RunRequest request) {
                                return engine.start(request);
                        }
                        @Override public ResumeResult resume(RunSnapshot snapshot) {
                                resumes.incrementAndGet();
                                throw new AssertionError("diagnostics must not resume snapshots");
                        }
                };
                RunSessionService diagnosticsService = new RunSessionService(new TestPackCatalog(pack), diagnosticsStore,
                                diagnosticsStore, diagnosticsStore, diagnosticsStore,
                                (ignoredOwner, runId, priority) -> noResumeEngine, CLOCK::instant, limits,
                                diagnosticsStore, DIRECT, new byte[32], diagnosticsStore,
                                ignored -> new LedgerScope("profile:profile-1", limits.profileCallsPerDay()));
                UUID runId = UUID.randomUUID();
                diagnosticsService.start(owner, new StartRequest(runId, PACK_ID, LOCALE, Map.of(), null, false),
                                Duration.ofSeconds(1));

                Diagnostics diagnostics = diagnosticsService.diagnostics(owner, runId);

                assertThat(resumes).hasValue(0);
                assertThat(diagnostics.generationCalls()).isZero();
                assertThat(diagnostics.stages()).isEmpty();
                assertThat(diagnostics.budget()).isEqualTo(new BudgetDiagnostics(40, 1500));
        }

    private static Pack pack() {
        var manifest = new PackManifest(1, PACK_ID, PACK_VERSION, "Test", "Test pack",
                List.of(LOCALE), LOCALE, Set.of(PackFeature.GENERATION), List.of(), List.of(),
                new ContentPolicy(false, new ContentPolicy.Audience(10, 14), List.of()), List.of());
        return new Pack(manifest, Map.of());
    }

    private static final Executor DIRECT = Runnable::run;

    private record TestPackCatalog(Pack pack) implements SessionPorts.PackCatalog {
        @Override public List<Pack> all() { return List.of(pack); }
        @Override public java.util.Optional<Pack> find(String id) {
            return pack.manifest().id().value().equals(id) ? java.util.Optional.of(pack) : java.util.Optional.empty();
        }
    }

    private static final class TestEngine implements Engine {
        private final UUID runId;
        private final Pack pack;

        private TestEngine(UUID runId, Pack pack) {
            this.runId = runId;
            this.pack = pack;
        }

        @Override
        public StartResult start(com.capybee.engine.api.RunRequest request) {
            return new StartResult(new TestRun(initialSnapshot(request)), List.of(), List.of());
        }

        @Override
        public ResumeResult resume(RunSnapshot snapshot) {
            return new ResumeResult(new TestRun(snapshot), List.of());
        }

        private RunSnapshot initialSnapshot(com.capybee.engine.api.RunRequest request) {
            Instant now = CLOCK.instant();
            RunState state = new RunState("start", List.of("start"), false, null);
            return new RunSnapshot(1, runId.toString(), new PackRef(pack.manifest().id(),
                    pack.manifest().version()), request.locale(), request.seed() == null ? 0 : request.seed(),
                    request.startInputs(), now, now, 0, state);
        }
    }

    private static final class TestRun implements Run {
        private RunSnapshot snapshot;
        private int applications;

        private TestRun(RunSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override public String id() { return snapshot.runId(); }
        @Override public View view() {
            return new View(List.of(), List.of(), null, List.of(), snapshot.state().ended(),
                    snapshot.state().endReason());
        }
        @Override public RunSnapshot snapshot() { return snapshot; }

        @Override
        public StepResult apply(Command command) {
            applications++;
            if (command instanceof Command.SubmitText submit && "reject".equals(submit.text())) {
                return new StepResult(false, view(), List.of(),
                        List.of(new Issue(Issue.Severity.WARNING, "INPUT_REJECTED", "Try another answer.")));
            }
            Instant now = CLOCK.instant();
            RunState old = snapshot.state();
            boolean ended = command instanceof Command.Abandon;
            EndReason reason = ended ? EndReason.ABANDONED : old.endReason();
            RunState next = new RunState("step-" + (snapshot.step() + 1),
                    List.of("start", "step-" + (snapshot.step() + 1)), ended, reason);
            snapshot = new RunSnapshot(snapshot.snapshotVersion(), snapshot.runId(), snapshot.pack(),
                    snapshot.locale(), snapshot.seed(), snapshot.startInputs(), snapshot.createdAt(), now,
                    snapshot.step() + 1, next);
            return new StepResult(true, view(), List.of(), List.of());
        }
    }
}
