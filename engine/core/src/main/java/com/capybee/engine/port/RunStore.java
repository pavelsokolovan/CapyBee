package com.capybee.engine.port;

import com.capybee.engine.api.RunSnapshot;

import java.util.Optional;

public interface RunStore {
    Optional<RunSnapshot> load(String runId);
    void save(RunSnapshot snapshot);
    void delete(String runId);
}