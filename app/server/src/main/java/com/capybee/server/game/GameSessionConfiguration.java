package com.capybee.server.game;

import com.capybee.engine.adapter.Randoms;
import com.capybee.engine.adapter.StructuredInputInterpreter;
import com.capybee.engine.adapter.UuidIdSource;
import com.capybee.engine.api.Engine;
import com.capybee.engine.pack.ClasspathPackSource;
import com.capybee.engine.pack.Pack;
import com.capybee.engine.pack.PackId;
import com.capybee.engine.pack.PackRegistry;
import com.capybee.engine.pack.json.PackLoader;
import com.capybee.engine.port.TextGenerator;
import com.capybee.engine.session.MeteredTextGenerator;
import com.capybee.engine.session.RunSessionService;
import com.capybee.engine.session.SessionLimits;
import com.capybee.engine.session.SessionPorts;
import com.capybee.engine.session.SessionTypes.LedgerScope;
import com.capybee.engine.session.SessionTypes.OwnerRef;
import com.capybee.server.repository.FamilyProfileRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import com.capybee.engine.adapter.anthropic.AnthropicConfig;
import com.capybee.engine.adapter.anthropic.AnthropicTextGenerator;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

@Configuration
public class GameSessionConfiguration {
    @Bean
    Clock gameSessionClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnProperty(name = "capybee.game.generation-enabled", havingValue = "true")
    TextGenerator gameTextGenerator() {
        return new AnthropicTextGenerator(AnthropicConfig.fromEnvironment());
    }

    @Bean
    PackRegistry gamePackRegistry(@Value("${capybee.game.pack-ids:}") String configuredPackIds) {
        if (configuredPackIds.isBlank()) return new PackRegistry(List.of());
        PackLoader loader = new PackLoader(Engine.sectionValidators());
        List<Pack> packs = Arrays.stream(configuredPackIds.split(","))
                .map(String::trim).filter(id -> !id.isEmpty())
                .map(id -> loader.load(new PackId(id), new ClasspathPackSource(
                        GameSessionConfiguration.class.getClassLoader(), "game-packs/" + id)))
                .toList();
        return new PackRegistry(packs);
    }

    @Bean
    SessionPorts.PackCatalog gamePackCatalog(PackRegistry gamePackRegistry) {
        return new SessionPorts.PackCatalog() {
            @Override public List<Pack> all() { return List.copyOf(gamePackRegistry.all()); }
            @Override public java.util.Optional<Pack> find(String packId) {
                try {
                    return gamePackRegistry.find(new PackId(packId));
                } catch (IllegalArgumentException invalidId) {
                    return java.util.Optional.empty();
                }
            }
        };
    }

    @Bean
    Executor gameSessionWorkers(
            @Value("${capybee.game.worker-threads:4}") int workerThreads) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, workerThreads));
        executor.setMaxPoolSize(Math.max(1, workerThreads));
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("capybee-game-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }

    @Bean
    SessionLimits gameSessionLimits(
            @Value("${capybee.game.lease-seconds:30}") long leaseSeconds,
            @Value("${capybee.game.start-ttl-seconds:600}") long startTtlSeconds,
            @Value("${capybee.game.limits.run-calls:24}") int runCalls,
            @Value("${capybee.game.limits.global-calls-per-day:1500}") int globalCalls,
            @Value("${capybee.game.max-concurrent-generation:2}") int maxConcurrent) {
        return new SessionLimits(java.time.Duration.ofSeconds(leaseSeconds),
                java.time.Duration.ofSeconds(startTtlSeconds), runCalls, globalCalls, maxConcurrent);
    }

    @Bean
    RunSessionService runSessionService(SessionPorts.PackCatalog packs,
            SessionPorts.RunRepository runs, SessionPorts.CommandLog commands,
            SessionPorts.GenerationLedger ledger, SessionPorts.EventOutbox outbox,
            SessionPorts.PrefetchStore prefetch, SessionPorts.EngineFactory engines,
            SessionPorts.SessionClock clock, SessionLimits limits,
            SessionPorts.Transaction transaction, @Qualifier("gameSessionWorkers") Executor gameSessionWorkers,
            @Value("${capybee.game.command-hmac-key:${APP_SECRET:}}") String configuredMacKey,
            @Value("${capybee.game.enabled:false}") boolean enabled) {
        if (enabled && configuredMacKey.isBlank()) {
            throw new IllegalStateException("GAME_COMMAND_MAC_KEY_REQUIRED");
        }
        byte[] key = configuredMacKey.isBlank() ? new byte[32] : decodeMacKey(configuredMacKey);
        return new RunSessionService(packs, runs, commands, outbox, prefetch, engines, clock,
                limits, transaction, gameSessionWorkers, key);
    }

    @Bean
    SessionPorts.SessionClock gameSessionClockPort(Clock gameSessionClock) {
        return gameSessionClock::instant;
    }

    @Bean
    GameCommandRateLimiter gameCommandRateLimiter(Clock gameSessionClock,
            @Value("${capybee.game.limits.commands-per-minute:60}") int commandsPerMinute) {
        return new GameCommandRateLimiter(gameSessionClock, commandsPerMinute);
    }

    @Bean
    SessionPorts.EngineFactory gameEngineFactory(PackRegistry registry, ObjectProvider<TextGenerator> generators,
            SessionPorts.GenerationLedger ledger, FamilyProfileRepository profiles, Clock gameSessionClock,
            @Value("${capybee.game.limits.profile-calls-per-day:40}") int profileCalls,
            @Value("${capybee.game.limits.global-calls-per-day:1500}") int globalCalls,
            @Value("${capybee.game.limits.run-calls:24}") int runCalls,
            @Value("${capybee.game.day-zone:UTC}") String dayZone,
            @Value("${capybee.game.prefetch.profile-reserve:8}") int profileReserve,
            @Value("${capybee.game.prefetch.global-reserve:100}") int globalReserve,
            @Value("${capybee.game.prefetch.run-reserve:4}") int runReserve) {
        return (owner, runId, priority) -> {
            TextGenerator provider = generators.getIfAvailable();
            var builder = Engine.builder(registry).clock(gameSessionClock)
                    .random(Randoms.seeded(runId.getMostSignificantBits() ^ runId.getLeastSignificantBits()))
                    .ids(new UuidIdSource());
            if (provider != null) {
                UUID userId = UUID.fromString(owner.value());
                String profileScope = profiles.findByParentUser_Id(userId)
                        .filter(com.capybee.server.domain.profile.FamilyProfile::isActive)
                        .map(profile -> "profile:" + profile.getId())
                        .orElseThrow(() -> new IllegalStateException("GAME_PROFILE_REQUIRED"));
                MeteredTextGenerator metered = new MeteredTextGenerator(provider, ledger,
                        new LedgerScope(profileScope, profileCalls),
                        new LedgerScope("global", globalCalls),
                        LocalDate.now(gameSessionClock.withZone(ZoneId.of(dayZone))),
                        runId, runCalls, priority, profileReserve, globalReserve, runReserve);
                builder.textGenerator(metered).inputInterpreter(new StructuredInputInterpreter(metered));
            }
            return builder.build();
        };
    }

    private static byte[] decodeMacKey(String configured) {
        byte[] raw = configured.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try {
            byte[] decoded = Base64.getDecoder().decode(configured);
            if (decoded.length >= 32) return decoded;
        } catch (IllegalArgumentException ignored) {
            if (raw.length >= 32) return raw;
        }
        if (raw.length >= 32) return raw;
        throw new IllegalStateException("GAME_COMMAND_MAC_KEY_INVALID");
    }
}
