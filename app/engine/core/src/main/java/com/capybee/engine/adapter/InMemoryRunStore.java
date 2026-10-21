package com.capybee.engine.adapter;

import com.capybee.engine.api.RunSnapshot;
import com.capybee.engine.port.RunStore;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryRunStore implements RunStore {
    private final ConcurrentMap<String, RunSnapshot> snapshots = new ConcurrentHashMap<>();

    @Override
    public Optional<RunSnapshot> load(String runId) {
        return Optional.ofNullable(snapshots.get(Objects.requireNonNull(runId, "runId")));
    }

    @Override
    public void save(RunSnapshot snapshot) {
        RunSnapshot value = Objects.requireNonNull(snapshot, "snapshot");
        snapshots.put(Objects.requireNonNull(value.runId(), "snapshot.runId"), value);
    }

    @Override
    public void delete(String runId) {
        snapshots.remove(Objects.requireNonNull(runId, "runId"));
    }
}