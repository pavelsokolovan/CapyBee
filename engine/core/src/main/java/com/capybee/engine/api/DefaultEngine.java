package com.capybee.engine.api;

import com.capybee.engine.adapter.Randoms;
import com.capybee.engine.content.Scene;
import com.capybee.engine.content.SceneSet;
import com.capybee.engine.content.json.SceneSets;
import com.capybee.engine.pack.*;
import com.capybee.engine.port.IdSource;
import com.capybee.engine.rules.*;
import com.capybee.engine.rules.json.RulesParser;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;

final class DefaultEngine implements Engine {
    private final PackRegistry packs;
    private final Clock clock;
    private final RandomGenerator random;
    private final IdSource ids;

    DefaultEngine(PackRegistry packs, Clock clock, RandomGenerator random, IdSource ids) {
        this.packs = packs;
        this.clock = clock;
        this.random = random;
        this.ids = ids;
    }

    @Override
    public StartResult start(RunRequest request) {
        List<Issue> issues = validateStartRequest(request);
        if (issues.stream().anyMatch(issue -> issue.severity() == Issue.Severity.ERROR)) {
            return new StartResult(null, issues);
        }

        Pack pack = packs.require(request.packId());
        LocaleTag locale = request.locale() == null ? pack.manifest().defaultLocale() : request.locale();
        SceneSet sceneSet = SceneSets.load(pack).get(locale);
        if (sceneSet == null || sceneSet.scenes().isEmpty()) {
            return new StartResult(null, List.of(new Issue(Issue.Severity.ERROR, "SCENES_MISSING",
                    "No scenes are available for locale " + locale.value())));
        }

        Scene startScene = sceneSet.scenes().get(sceneSet.startId());
        if (startScene == null) {
            return new StartResult(null, List.of(new Issue(Issue.Severity.ERROR, "SCENE_START_MISSING",
                    "The pack has no start scene for locale " + locale.value())));
        }

        Map<String, String> normalizedInputs = normalizeInputs(request.startInputs(), pack);
        long seed = request.seed() != null ? request.seed() : random.nextLong();
        String runId = ids.nextId();
        Instant now = clock.instant();
        List<String> visited = new ArrayList<>();
        visited.add(startScene.id());
        Rules rules = RulesParser.parse(pack);
        RunImpl run = new RunImpl(runId, pack, locale, seed, normalizedInputs, now, now, 0L,
            startScene.id(), visited, false, null, sceneSet, clock, rules, null);
        issues.addAll(run.startupIssues());
        return new StartResult(run, issues, run.startupEvents());
    }

    @Override
    public ResumeResult resume(RunSnapshot snapshot) {
        List<Issue> issues = new ArrayList<>();
        if (snapshot == null) {
            issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID", "Snapshot is missing"));
            return new ResumeResult(null, issues);
        }
        if (snapshot.snapshotVersion() != 1 || snapshot.pack() == null || snapshot.pack().id() == null
            || snapshot.pack().version() == null || snapshot.state() == null || snapshot.runId() == null
            || snapshot.runId().isBlank() || snapshot.createdAt() == null || snapshot.updatedAt() == null
            || snapshot.step() < 0) {
            issues.add(new Issue(Issue.Severity.ERROR, snapshot.snapshotVersion() == 1 ? "SNAPSHOT_INVALID" : "SNAPSHOT_VERSION_UNSUPPORTED",
                    "Snapshot version, pack, or state is invalid"));
            return new ResumeResult(null, issues);
        }
        Pack pack = packs.find(snapshot.pack().id()).orElse(null);
        if (pack == null) {
            issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_PACK_MISMATCH", "Snapshot pack is not loaded"));
            return new ResumeResult(null, issues);
        }
        PackVersion loadedVersion = pack.manifest().version();
        PackVersion savedVersion = snapshot.pack().version();
        if (loadedVersion.major() != savedVersion.major()) {
            issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_PACK_MISMATCH",
                    "Snapshot pack major version does not match loaded pack"));
            return new ResumeResult(null, issues);
        }
        boolean migrating = !loadedVersion.equals(savedVersion);

        LocaleTag locale = snapshot.locale();
        if (locale == null) {
            locale = pack.manifest().defaultLocale();
        }
        if (locale == null || !pack.manifest().locales().contains(locale)) {
            issues.add(new Issue(Issue.Severity.ERROR, "LOCALE_UNSUPPORTED", "Locale not declared by the pack: " + locale));
            return new ResumeResult(null, issues);
        }

        SceneSet sceneSet = SceneSets.load(pack).get(locale);
        if (sceneSet == null) {
            issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID",
                    "Snapshot locale has no scenes: " + locale.value()));
            return new ResumeResult(null, issues);
        }

        String currentSceneId = snapshot.state().sceneId();
        if (!sceneSet.scenes().containsKey(currentSceneId)) {
            issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID",
                    "Snapshot state references unknown scene: " + currentSceneId));
            return new ResumeResult(null, issues);
        }
        Rules rules = RulesParser.parse(pack);
        RunState restoredState = migrateState(snapshot.state(), rules, sceneSet, migrating, issues);
        if (issues.stream().anyMatch(issue -> issue.severity() == Issue.Severity.ERROR)) {
            return new ResumeResult(null, issues);
        }

        RunImpl run = new RunImpl(snapshot.runId(), pack, locale, snapshot.seed(), snapshot.startInputs(),
                snapshot.createdAt(), snapshot.updatedAt(), snapshot.step(), currentSceneId,
                new ArrayList<>(restoredState.visited()), restoredState.ended(), restoredState.endReason(),
                sceneSet, clock, rules, restoredState);
        return new ResumeResult(run, issues);
    }

    private RunState migrateState(RunState saved, Rules rules, SceneSet scenes, boolean migrating, List<Issue> issues) {
        boolean pruned = false;
        boolean clamped = false;
        if (saved.ended() == (saved.endReason() == null)) {
            issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID", "Snapshot ended state and end reason are inconsistent"));
        }
        Map<String, CounterDef> counterDefs = new LinkedHashMap<>();
        Map<String, Integer> counters = new LinkedHashMap<>();
        rules.counters().forEach(def -> {
            counterDefs.put(def.id(), def);
            counters.put(def.id(), def.initial());
        });
        for (var entry : saved.counters().entrySet()) {
            CounterDef def = counterDefs.get(entry.getKey());
            if (def == null) {
                if (migrating) pruned = true;
                else issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID", "Unknown snapshot counter: " + entry.getKey()));
                continue;
            }
            int value = entry.getValue();
            if (value < def.min() || value > def.max()) {
                if (migrating) {
                    value = Math.max(def.min(), Math.min(def.max(), value));
                    clamped = true;
                } else {
                    issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID", "Snapshot counter is outside bounds: " + entry.getKey()));
                    continue;
                }
            }
            counters.put(entry.getKey(), value);
        }

        Map<String, Boolean> flags = new LinkedHashMap<>();
        Set<String> flagIds = new HashSet<>();
        rules.flags().forEach(def -> {
            flagIds.add(def.id());
            flags.put(def.id(), def.initial());
        });
        for (var entry : saved.flags().entrySet()) {
            if (!flagIds.contains(entry.getKey())) {
                if (migrating) pruned = true;
                else issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID", "Unknown snapshot flag: " + entry.getKey()));
            } else flags.put(entry.getKey(), entry.getValue());
        }

        Map<String, SetDef> setDefs = new LinkedHashMap<>();
        Map<String, List<String>> sets = new LinkedHashMap<>();
        rules.sets().forEach(def -> {
            setDefs.put(def.id(), def);
            sets.put(def.id(), def.initial().stream().sorted().toList());
        });
        for (var entry : saved.sets().entrySet()) {
            SetDef def = setDefs.get(entry.getKey());
            if (def == null) {
                if (migrating) pruned = true;
                else issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID", "Unknown snapshot set: " + entry.getKey()));
                continue;
            }
            Set<String> seen = new HashSet<>();
            List<String> members = new ArrayList<>();
            for (String member : entry.getValue()) {
                if (!seen.add(member)) {
                    if (migrating) pruned = true;
                    else issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID", "Duplicate snapshot set member: " + entry.getKey()));
                } else if (!def.values().contains(member)) {
                    if (migrating) pruned = true;
                    else issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID", "Unknown snapshot set member: " + entry.getKey()));
                } else members.add(member);
            }
            sets.put(entry.getKey(), members.stream().sorted().toList());
        }

        Map<String, ObjectiveState> objectives = new LinkedHashMap<>();
        Set<String> objectiveIds = new HashSet<>();
        rules.objectives().forEach(def -> {
            objectiveIds.add(def.id());
            objectives.put(def.id(), ObjectiveState.ACTIVE);
        });
        for (var entry : saved.objectives().entrySet()) {
            if (!objectiveIds.contains(entry.getKey())) {
                if (migrating) pruned = true;
                else issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID", "Unknown snapshot objective: " + entry.getKey()));
            } else objectives.put(entry.getKey(), entry.getValue());
        }

        Set<String> outcomeIds = new HashSet<>();
        rules.outcomes().forEach(def -> outcomeIds.add(def.id()));
        String outcome = saved.outcome();
        if (outcome != null && !outcomeIds.contains(outcome)) {
            if (migrating) {
                outcome = null;
                pruned = true;
            } else issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID", "Snapshot outcome is not declared"));
        }

        List<String> visited = new ArrayList<>();
        Set<String> seenScenes = new HashSet<>();
        for (String visitedId : saved.visited()) {
            if (!seenScenes.add(visitedId)) {
                if (migrating) pruned = true;
                else issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID", "Snapshot visited list contains duplicates"));
            } else if (!scenes.scenes().containsKey(visitedId)) {
                if (migrating) pruned = true;
                else issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID", "Snapshot visited list references unknown scene: " + visitedId));
            } else visited.add(visitedId);
        }
        if (!seenScenes.contains(saved.sceneId())) {
            if (migrating) visited.add(saved.sceneId());
            else issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID", "Current scene is missing from visited list"));
        }
        if (pruned) issues.add(new Issue(Issue.Severity.WARNING, "SNAPSHOT_STATE_PRUNED", "Obsolete snapshot state was removed"));
        if (clamped) issues.add(new Issue(Issue.Severity.WARNING, "SNAPSHOT_STATE_CLAMPED", "Snapshot counters were clamped to current bounds"));
        return new RunState(saved.sceneId(), visited, saved.ended(), saved.endReason(), counters, flags, sets, objectives, outcome);
    }

    private List<Issue> validateStartRequest(RunRequest request) {
        List<Issue> issues = new ArrayList<>();
        if (request == null) {
            issues.add(new Issue(Issue.Severity.ERROR, "PACK_NOT_FOUND", "Run request is missing"));
            return issues;
        }

        Pack pack = packs.find(request.packId()).orElse(null);
        if (pack == null) {
            issues.add(new Issue(Issue.Severity.ERROR, "PACK_NOT_FOUND", "Pack not found: " + request.packId()));
            return issues;
        }

        LocaleTag locale = request.locale() == null ? pack.manifest().defaultLocale() : request.locale();
        if (locale == null || !pack.manifest().locales().contains(locale)) {
            issues.add(new Issue(Issue.Severity.ERROR, "LOCALE_UNSUPPORTED", "Locale not declared by the pack: " + locale));
        }

        Set<PackFeature> unsupported = new HashSet<>();
        for (PackFeature feature : pack.manifest().features()) {
            if (feature == PackFeature.GENERATION) {
                unsupported.add(feature);
            }
            if (feature != PackFeature.PLACES && feature != PackFeature.ASSETS && feature != PackFeature.GENERATION) {
                unsupported.add(feature);
            }
        }
        if (!unsupported.isEmpty()) {
            issues.add(new Issue(Issue.Severity.ERROR, "FEATURE_NOT_AVAILABLE",
                    "Pack feature not supported by this engine version: " + unsupported.iterator().next()));
        }

        Map<String, StartInputDef> declared = new LinkedHashMap<>();
        for (StartInputDef input : pack.manifest().startInputs()) {
            declared.put(input.id(), input);
        }

        Map<String, String> normalized = normalizeInputs(request.startInputs(), pack);
        for (String key : normalized.keySet()) {
            if (!declared.containsKey(key)) {
                issues.add(new Issue(Issue.Severity.ERROR, "START_INPUT_UNKNOWN",
                        "Unknown start input: " + key));
            }
        }

        for (StartInputDef input : pack.manifest().startInputs()) {
            String value = normalized.get(input.id());
            if (value == null || value.isBlank()) {
                if (input.required()) {
                    issues.add(new Issue(Issue.Severity.ERROR, "START_INPUT_MISSING",
                            "Required start input is missing: " + input.id()));
                }
                continue;
            }
            if (!inputValueValid(input, value)) {
                issues.add(new Issue(Issue.Severity.ERROR, "START_INPUT_INVALID",
                        "Start input value is invalid for " + input.id()));
            }
        }

        return issues;
    }

    private Map<String, String> normalizeInputs(Map<String, String> inputMap, Pack pack) {
        Map<String, String> normalized = new LinkedHashMap<>();
        if (inputMap == null || inputMap.isEmpty()) {
            return normalized;
        }
        for (Map.Entry<String, String> entry : inputMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || key.isBlank()) {
                continue;
            }
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            normalized.put(key, trimmed);
        }
        return normalized;
    }

    private boolean inputValueValid(StartInputDef input, String value) {
        if (input instanceof StartInputDef.EnumInput enumInput) {
            return enumInput.values().contains(value);
        }
        if (input instanceof StartInputDef.EnumSetInput enumSetInput) {
            String[] parts = value.split(",");
            if (parts.length > enumSetInput.maxItems()) {
                return false;
            }
            Set<String> seen = new HashSet<>();
            for (String part : parts) {
                String trimmed = part.trim();
                if (trimmed.isEmpty() || !enumSetInput.values().contains(trimmed) || !seen.add(trimmed)) {
                    return false;
                }
            }
            return true;
        }
        if (input instanceof StartInputDef.FreeTextInput freeTextInput) {
            return value.length() <= freeTextInput.maxChars();
        }
        return false;
    }
}
