package com.capybee.engine.api;

import com.capybee.engine.pack.LocaleTag;

import java.time.Instant;
import java.util.Map;

public record RunSnapshot(int snapshotVersion, String runId, PackRef pack, DynamicSnapshotSource dynamic,
                          LocaleTag locale, long seed, Map<String, String> startInputs,
                          Instant createdAt, Instant updatedAt, long step, RunState state) {
    public RunSnapshot {
        startInputs = Map.copyOf(startInputs);
        if (snapshotVersion == 1 && (pack == null || dynamic != null))
            throw new IllegalArgumentException("Invalid Pack snapshot source");
        if (snapshotVersion == 2 && (pack != null || dynamic == null || !startInputs.isEmpty()
                || !state.generationArtifacts().isEmpty()))
            throw new IllegalArgumentException("Invalid dynamic snapshot source");
        if (snapshotVersion != 1 && snapshotVersion != 2)
            throw new IllegalArgumentException("Unsupported snapshot version");
    }

    public RunSnapshot(int snapshotVersion, String runId, PackRef pack, LocaleTag locale, long seed,
                       Map<String, String> startInputs, Instant createdAt, Instant updatedAt,
                       long step, RunState state) {
        this(snapshotVersion, runId, pack, null, locale, seed, startInputs, createdAt, updatedAt, step, state);
    }

    public boolean dynamicGame() { return dynamic != null; }
}
