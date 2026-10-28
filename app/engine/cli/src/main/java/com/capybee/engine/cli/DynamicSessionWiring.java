package com.capybee.engine.cli;

import com.capybee.engine.adapter.Randoms;
import com.capybee.engine.api.*;
import com.capybee.engine.generation.blueprint.DefaultBlueprintOutputReviewer;
import com.capybee.engine.generation.profile.*;
import com.capybee.engine.input.DefaultInputGuard;
import com.capybee.engine.pack.Pack;
import com.capybee.engine.pack.PackRegistry;
import com.capybee.engine.port.IdSource;
import com.capybee.engine.port.TextGenerator;
import com.capybee.engine.session.*;
import com.capybee.engine.session.adapter.InMemorySessionStore;
import com.capybee.engine.session.adapter.LocalSessionStore;

import java.io.InputStream;
import java.io.PrintStream;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.random.RandomGenerator;

final class DynamicSessionWiring {
    private DynamicSessionWiring() {}

    static int run(ConsoleOptions options, ValidatedGeneratorProfile profile, InputStream in, PrintStream out,
            PrintStream err, Clock clock, IdSource ids, RandomGenerator random, TextGenerator generator,
            ConsoleTuning tuning, SessionPorts.SessionStore suppliedStore) {
        LocalSessionStore local = null;
        SessionPorts.SessionStore store = suppliedStore;
        if (store == null && options.storeDir() == null) store = new InMemorySessionStore(clock);
        else if (store == null) { local = LocalSessionStore.open(options.storeDir(), clock); store = local; }
        byte[] key = local == null ? new byte[32] : local.commandKey();
        SessionLimits limits = SessionLimits.defaults();
        SessionPorts.SessionStore selectedStore = store;
        SessionPorts.PackCatalog catalog = new SessionPorts.PackCatalog() {
            public List<Pack> all() { return List.of(); }
            public java.util.Optional<Pack> find(String id) { return java.util.Optional.empty(); }
        };
        SessionPorts.EngineFactory resumeFactory = (owner, runId, priority) -> {
            var builder = Engine.builder(new PackRegistry(List.of())).clock(clock)
                    .random(Randoms.seeded(runId.getMostSignificantBits() ^ runId.getLeastSignificantBits())).ids(ids);
            if (generator != null) builder.textGenerator(generator);
            return builder.build();
        };
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            RunSessionService sessions = new RunSessionService(catalog, store, store, store, store, resumeFactory,
                    clock::instant, limits, store, workers, key);
            SessionTypes.OwnerRef owner = new SessionTypes.OwnerRef("local-console");
            SessionConsole console = new SessionConsole(in, out, err, tuning);
            SessionTypes.RunSummary current = options.runId() == null
                    ? store.findCurrent(owner).map(v -> sessions.get(owner, v.id())).orElse(null)
                    : sessions.get(owner, options.runId());
            SessionConsole.StartupChoice choice = console.startup(options, current);
            if (choice == SessionConsole.StartupChoice.QUIT) return 0;
            if (choice == SessionConsole.StartupChoice.ERROR) { err.println("STARTUP_AMBIGUOUS"); return 2; }
            if (choice == SessionConsole.StartupChoice.CONTINUE) return console.run(options, sessions, owner, current);
            if (profile == null) { err.println("PROFILE_REQUIRED_FOR_NEW_DYNAMIC_GAME"); return 2; }
            if (generator == null || options.offline()) {
                err.println("DYNAMIC_PROVIDER_UNAVAILABLE: a new AI-generated game cannot be created offline; choose a reviewed Pack fallback or cancel.");
                return 4;
            }
            var locale = options.locale() == null ? profile.profile().defaultLocale() : options.locale();
            DynamicSetup.Result setup = console.dynamicSetup(options, profile, locale, true);
            if (setup.cancelled()) return 0;
            PreparationResolution resolution = new PreparationPresetResolver().resolve(profile, setup.answers(), locale,
                    HostGenerationCeilings.none());
            if (!resolution.valid()) { err.println("DYNAMIC_PREPARATION_INVALID"); return 2; }
            var coordinator = new DynamicStartCoordinator(sessions, new SessionGenerators(generator, store, limits),
                    new DefaultBlueprintOutputReviewer(), new DefaultInputGuard(), ids, clock);
            var preparation = new ConfirmedDynamicPreparation(profile, resolution.preferences().orElseThrow(),
                    resolution.constraints().orElseThrow(), setup.revision(), setup.suggestion());
            UUID requestId = UUID.randomUUID();
            var result = coordinator.startDynamic(owner, requestId, preparation, options.seed(),
                    current != null && current.status() == SessionTypes.RunStatus.ACTIVE ? current.runId() : null,
                    tuning.maxWait());
            if (result instanceof DynamicStartCoordinator.FallbackOffered offered) { err.println(offered.code()); return 4; }
            if (result instanceof DynamicStartCoordinator.NeedsInput needed) { err.println(needed.code()); return 4; }
            if (result instanceof DynamicStartCoordinator.Cancelled cancelled) { err.println(cancelled.code()); return 4; }
            if (result instanceof DynamicStartCoordinator.Failed failed) { err.println(failed.code()); return 4; }
            current = ((DynamicStartCoordinator.Started) result).run();
            out.println("CapyBee — new dynamic game");
            out.println("Language: " + locale.value() + "\nSaving: " + (options.memory() ? "temporary" : "on"));
            return console.run(options, sessions, owner, current);
        } catch (SessionException failure) {
            err.println(failure.code().name()); return 3;
        } catch (RuntimeException failure) {
            err.println("DYNAMIC_START_UNAVAILABLE"); return 4;
        } finally { if (local != null) local.close(); }
    }
}
