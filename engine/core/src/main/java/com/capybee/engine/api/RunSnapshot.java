package com.capybee.engine.api;

import com.capybee.engine.pack.LocaleTag;

import java.time.Instant;
import java.util.Map;

public record RunSnapshot(int snapshotVersion, String runId, PackRef pack, LocaleTag locale, long seed,
                          Map<String, String> startInputs, Instant createdAt, Instant updatedAt,
                          long step, RunState state) {
    public RunSnapshot {
        startInputs = Map.copyOf(startInputs);
    }
}
