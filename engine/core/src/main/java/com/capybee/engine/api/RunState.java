package com.capybee.engine.api;

import java.util.List;

public record RunState(String sceneId, List<String> visited, boolean ended, EndReason endReason) {
    public RunState {
        visited = List.copyOf(visited);
    }
}
