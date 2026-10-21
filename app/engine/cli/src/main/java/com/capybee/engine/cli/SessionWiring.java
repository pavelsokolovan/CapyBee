package com.capybee.engine.cli;

import com.capybee.engine.adapter.Randoms;
import com.capybee.engine.adapter.StructuredInputInterpreter;
import com.capybee.engine.api.Engine;
import com.capybee.engine.pack.Pack;
import com.capybee.engine.pack.PackId;
import com.capybee.engine.pack.PackRegistry;
import com.capybee.engine.port.IdSource;
import com.capybee.engine.port.TextGenerator;
import com.capybee.engine.session.MeteredTextGenerator;
import com.capybee.engine.session.RunSessionService;
import com.capybee.engine.session.SessionGenerators;
import com.capybee.engine.session.SessionException;
import com.capybee.engine.session.SessionLimits;
import com.capybee.engine.session.SessionPorts;
import com.capybee.engine.session.SessionTypes;
import com.capybee.engine.session.StoreException;
import com.capybee.engine.session.adapter.InMemorySessionStore;
import com.capybee.engine.session.adapter.LocalSessionStore;

import java.io.InputStream;
import java.io.PrintStream;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.random.RandomGenerator;

final class SessionWiring {
    private SessionWiring() {}

    static int run(ConsoleOptions options, PackId packId, Pack pack, InputStream in, PrintStream out,
                   PrintStream err, Clock clock, IdSource ids, RandomGenerator random,
                   TextGenerator textGenerator, ConsoleTuning tuning) {
        LocalSessionStore local = null;
        SessionPorts.SessionStore store;
        if (options.storeDir() == null) store = new InMemorySessionStore(clock);
        else { local = LocalSessionStore.open(options.storeDir(), clock); store = local; }
        byte[] commandKey = local == null ? new byte[32] : local.commandKey();
        SessionLimits defaults = SessionLimits.defaults();
        int callsPerDay = options.callsPerDay() == null ? defaults.profileCallsPerDay() : options.callsPerDay();
        int globalCallsPerDay = options.callsPerDay() == null
            ? defaults.globalCallsPerDay() : options.callsPerDay();
        int runCalls = options.runCalls() == null ? defaults.runCalls() : options.runCalls();
        boolean prefetch = options.prefetch() == null ? options.generated() : options.prefetch();
        SessionLimits limits = new SessionLimits(defaults.leaseDuration(), defaults.startTtl(), runCalls,
            globalCallsPerDay, defaults.maxConcurrentGeneration(), callsPerDay, prefetch,
            defaults.prefetchMaxFanout(), defaults.prefetchMaxConcurrent(),
            defaults.prefetchProfileReserve(), defaults.prefetchGlobalReserve(), defaults.prefetchRunReserve());
        SessionTypes.LedgerScope profile = new SessionTypes.LedgerScope("profile:local", limits.profileCallsPerDay());
        SessionGenerators generators = textGenerator == null ? null : new SessionGenerators(textGenerator, store, limits);
        SessionPorts.PackCatalog catalog = new SessionPorts.PackCatalog() {
            @Override public List<Pack> all() { return List.of(pack); }
            @Override public java.util.Optional<Pack> find(String id) {
                return pack.manifest().id().value().equals(id) ? java.util.Optional.of(pack)
                        : java.util.Optional.empty();
            }
        };
        SessionPorts.EngineFactory factory = (owner, runId, priority) -> {
            var builder = Engine.builder(new PackRegistry(List.of(pack))).clock(clock)
                    .random(Randoms.seeded(runId.getMostSignificantBits() ^ runId.getLeastSignificantBits()))
                    .ids(ids);
                if (generators != null) {
                var generator = generators.forRun(profile, LocalDate.now(clock), runId, priority);
                builder.textGenerator(generator).inputInterpreter(new StructuredInputInterpreter(generator));
            }
            return builder.build();
        };
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            RunSessionService sessions = new RunSessionService(catalog, store, store, store, store,
                    factory, clock::instant, limits, store, workers, commandKey, store,
                    new SessionPorts.OwnerScopes() {
                        @Override public SessionTypes.LedgerScope profileScope(SessionTypes.OwnerRef ignored) {
                            return profile;
                        }
                        @Override public LocalDate currentDay(java.time.Instant instant) {
                            return LocalDate.ofInstant(instant, clock.getZone());
                        }
                    }, generators == null ? new com.capybee.engine.session.SingleFlightRegistry()
                            : generators.registry());
            SessionTypes.OwnerRef owner = new SessionTypes.OwnerRef("local-console");
            SessionTypes.RunSummary summary;
            if (options.runId() != null) {
                summary = sessions.get(owner, options.runId());
            } else if (!options.newRun() && store.findCurrent(owner).isPresent()) {
                summary = sessions.get(owner, store.findCurrent(owner).orElseThrow().id());
                if (options.locale() != null || !options.inputs().isEmpty() || options.seed() != null || options.generated()) {
                    out.println("Note: resuming run " + summary.runId() + "; start options are ignored. Use --new to start over.");
                }
            } else {
                if (options.newRun() && store.findCurrent(owner).isPresent()) {
                    UUID previous = store.findCurrent(owner).orElseThrow().id();
                    sessions.delete(owner, previous);
                    out.println("Discarded previous run " + previous + ".");
                }
                SessionTypes.StartRequest start = new SessionTypes.StartRequest(UUID.randomUUID(), packId,
                        options.locale() == null ? pack.manifest().defaultLocale() : options.locale(),
                        options.inputs(), options.seed(), options.generated());
                SessionTypes.StartAccepted accepted = sessions.start(owner, start, tuning.syncWait());
                summary = sessions.get(owner, accepted.runId());
            }
            SessionConsole console = new SessionConsole(in, out, err, tuning);
            summary = console.awaitActive(sessions, owner, summary);
            if (summary == null) return 3;
            return console.run(options, sessions, owner, summary);
        } catch (SessionException failure) {
            err.println(failure.code().name());
            err.println("  " + ConsoleErrors.sessionHint(failure.code().name()));
            return 3;
        } catch (StoreException failure) {
            err.println(failure.code().name());
            err.println("  " + ConsoleErrors.storeHint(failure.code()));
            return 3;
        } catch (RuntimeException failure) {
            err.println("SESSION_UNAVAILABLE");
            return 3;
        } finally {
            if (local != null) local.close();
        }
    }
}
