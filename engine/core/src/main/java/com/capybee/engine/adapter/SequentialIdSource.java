package com.capybee.engine.adapter;

import com.capybee.engine.port.IdSource;

import java.util.concurrent.atomic.AtomicLong;

public final class SequentialIdSource implements IdSource {
    private final String prefix;
    private final AtomicLong counter = new AtomicLong(1L);

    public SequentialIdSource(String prefix) {
        this.prefix = prefix == null ? "run" : prefix;
    }

    @Override
    public String nextId() {
        return prefix + "-" + counter.getAndIncrement();
    }
}
