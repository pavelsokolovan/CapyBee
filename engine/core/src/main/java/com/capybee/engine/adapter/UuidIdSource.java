package com.capybee.engine.adapter;

import com.capybee.engine.port.IdSource;

import java.util.UUID;

public final class UuidIdSource implements IdSource {
    @Override
    public String nextId() {
        return UUID.randomUUID().toString();
    }
}
