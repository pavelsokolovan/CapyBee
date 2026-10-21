package com.capybee.engine.generation;

import java.util.*;

/** Pure deterministic selection of bounded narrative facts from visited generated scenes. */
public final class StoryContinuity {
    private StoryContinuity() {}

    public record ContinuityFact(String sceneId, String phaseId, String text) {}

    public static List<ContinuityFact> selectContinuity(List<String> visitedSceneIds,
            Map<String, GenerationArtifact> acceptedScenes, Map<String, String> scenePhases,
            String currentSceneId, String currentPhaseId, int maxFacts, int maxCodePoints) {
        int current = phaseIndex(currentPhaseId, scenePhases, visitedSceneIds);
        List<Candidate> candidates = new ArrayList<>();
        Map<String, ContinuityFact> earliest = new LinkedHashMap<>();
        for (int visit = 0; visit < visitedSceneIds.size(); visit++) {
            String sceneId = visitedSceneIds.get(visit);
            if (sceneId.equals(currentSceneId)) continue;
            GenerationArtifact artifact = acceptedScenes.get(sceneId);
            if (artifact == null || !(artifact.payload() instanceof GenerationArtifact.PlannedScenePayload payload)) continue;
            String phase = scenePhases.get(sceneId);
            for (String fact : payload.continuityFacts()) {
                String key = fact.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
                ContinuityFact value = new ContinuityFact(sceneId, phase, fact);
                if (earliest.putIfAbsent(key, value) == null)
                    candidates.add(new Candidate(value, visit, priority(phase, current, scenePhases, visitedSceneIds)));
            }
        }
        candidates.sort(Comparator.comparingInt(Candidate::priority).thenComparing(Candidate::visit).reversed());
        List<Candidate> chosen = new ArrayList<>(); int points = 0;
        for (Candidate candidate : candidates) {
            int length = candidate.fact().text().codePointCount(0, candidate.fact().text().length());
            if (chosen.size() < maxFacts && points + length <= maxCodePoints) { chosen.add(candidate); points += length; }
        }
        chosen.sort(Comparator.comparingInt(Candidate::visit));
        return chosen.stream().map(Candidate::fact).toList();
    }

    private static int priority(String phase, int current, Map<String, String> phases, List<String> visited) {
        int index = phaseIndex(phase, phases, visited); return index == current ? 2 : index == current - 1 ? 1 : 0;
    }
    private static int phaseIndex(String phase, Map<String, String> phases, List<String> visited) {
        LinkedHashSet<String> order = new LinkedHashSet<>(); visited.stream().map(phases::get).filter(Objects::nonNull).forEach(order::add);
        if (phase != null) order.add(phase); return new ArrayList<>(order).indexOf(phase);
    }
    private record Candidate(ContinuityFact fact, int visit, int priority) {}
}
