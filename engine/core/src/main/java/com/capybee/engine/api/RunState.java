package com.capybee.engine.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record RunState(String sceneId, List<String> visited, boolean ended, EndReason endReason,
                       Map<String, Integer> counters, Map<String, Boolean> flags,
                       Map<String, List<String>> sets, Map<String, ObjectiveState> objectives,
                       String outcome, String currentPlaceId, List<String> visitedPlaces) {
    public RunState {
        visited = List.copyOf(visited == null ? List.of() : visited);
        counters = immutableMap(counters);
        flags = immutableMap(flags);
        Map<String, List<String>> copiedSets = new LinkedHashMap<>();
        if (sets != null) {
            sets.forEach((id, values) -> copiedSets.put(id, values.stream().sorted().toList()));
        }
        sets = Collections.unmodifiableMap(copiedSets);
        objectives = immutableMap(objectives);
        visitedPlaces = List.copyOf(visitedPlaces == null ? List.of() : visitedPlaces);
    }

    public RunState(String sceneId, List<String> visited, boolean ended, EndReason endReason,
                    Map<String, Integer> counters, Map<String, Boolean> flags,
                    Map<String, List<String>> sets, Map<String, ObjectiveState> objectives, String outcome) {
        this(sceneId, visited, ended, endReason, counters, flags, sets, objectives, outcome, null, List.of());
    }

    public RunState(String sceneId, List<String> visited, boolean ended, EndReason endReason) {
        this(sceneId, visited, ended, endReason, Map.of(), Map.of(), Map.of(), Map.of(), null, null, List.of());
    }

    private static <K, V> Map<K, V> immutableMap(Map<K, V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values == null ? Map.of() : values));
    }
}
