package com.capybee.engine.generation;
import java.util.List;
import java.util.Objects;
public record RunPhase(String id, PhaseKind kind, List<String> sceneIds) {
    public RunPhase { Objects.requireNonNull(id); Objects.requireNonNull(kind); sceneIds = List.copyOf(sceneIds); }
}
