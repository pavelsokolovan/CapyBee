package com.capybee.engine.api;

import com.capybee.engine.pack.PackRegistry;
import com.capybee.engine.port.IdSource;
import com.capybee.engine.port.InputGuard;
import com.capybee.engine.port.InputInterpreter;

import java.time.Clock;
import java.util.Objects;
import java.util.random.RandomGenerator;

public final class EngineBuilder {
    private final PackRegistry packs;
    private Clock clock;
    private RandomGenerator random;
    private IdSource ids;
    private InputGuard inputGuard;
    private InputInterpreter inputInterpreter;

    EngineBuilder(PackRegistry packs) {
        this.packs = Objects.requireNonNull(packs, "packs");
    }

    public EngineBuilder clock(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        return this;
    }

    public EngineBuilder random(RandomGenerator random) {
        this.random = Objects.requireNonNull(random, "random");
        return this;
    }

    public EngineBuilder ids(IdSource ids) {
        this.ids = Objects.requireNonNull(ids, "ids");
        return this;
    }

    public EngineBuilder inputGuard(InputGuard inputGuard) {
        this.inputGuard = Objects.requireNonNull(inputGuard, "inputGuard");
        return this;
    }

    public EngineBuilder inputInterpreter(InputInterpreter inputInterpreter) {
        this.inputInterpreter = Objects.requireNonNull(inputInterpreter, "inputInterpreter");
        return this;
    }

    public Engine build() {
        if (clock == null || random == null || ids == null) {
            throw new IllegalStateException("Engine ports must be configured: clock, random, ids");
        }
        return new DefaultEngine(packs, clock, random, ids, inputGuard, inputInterpreter);
    }
}
