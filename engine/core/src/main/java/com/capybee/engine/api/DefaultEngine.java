package com.capybee.engine.api;

import com.capybee.engine.adapter.Randoms;
import com.capybee.engine.content.Scene;
import com.capybee.engine.content.SceneSet;
import com.capybee.engine.content.json.SceneSets;
import com.capybee.engine.pack.*;
import com.capybee.engine.port.IdSource;
import com.capybee.engine.port.InputGuard;
import com.capybee.engine.port.InputInterpreter;
import com.capybee.engine.input.InputPolicy;
import com.capybee.engine.input.InputSafetyAction;
import com.capybee.engine.input.InputSafetyDecision;
import com.capybee.engine.input.PiiScrubber;
import com.capybee.engine.input.ProcessedInput;
import com.capybee.engine.pack.StartInputDef;
import com.capybee.engine.rules.*;
import com.capybee.engine.rules.json.RulesParser;
import com.capybee.engine.places.PlaceMap;
import com.capybee.engine.places.json.PlacesParser;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.random.RandomGenerator;

final class DefaultEngine implements Engine {
    private final PackRegistry packs;
    private final Clock clock;
    private final RandomGenerator random;
    private final IdSource ids;
    private final InputGuard inputGuard;
    private final InputInterpreter inputInterpreter;
    private final PiiScrubber piiScrubber = new PiiScrubber();
    private final Map<PackId, PlaceMap> placeMaps = new ConcurrentHashMap<>();

    DefaultEngine(PackRegistry packs, Clock clock, RandomGenerator random, IdSource ids,
                  InputGuard inputGuard, InputInterpreter inputInterpreter) {
        this.packs = packs;
        this.clock = clock;
        this.random = random;
        this.ids = ids;
        this.inputGuard = inputGuard;
        this.inputInterpreter = inputInterpreter;
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

        Map<String, String> normalizedInputs = persistentInputs(normalizeInputs(request.startInputs(), pack), pack);
        long seed = request.seed() != null ? request.seed() : random.nextLong();
        String runId = ids.nextId();
        Instant now = clock.instant();
        List<String> visited = new ArrayList<>();
        visited.add(startScene.id());
        Rules rules = RulesParser.parse(pack);
        PlaceMap placeMap = placeMap(pack);
        RunImpl run = new RunImpl(runId, pack, locale, seed, normalizedInputs, now, now, 0L,
            startScene.id(), visited, false, null, sceneSet, clock, rules, null, placeMap,
                inputGuard, inputInterpreter, piiScrubber);
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
        PlaceMap placeMap = placeMap(pack);
        RunState restoredState = migrateState(snapshot.state(), rules, sceneSet, placeMap, migrating, issues);
        if (issues.stream().anyMatch(issue -> issue.severity() == Issue.Severity.ERROR)) {
            return new ResumeResult(null, issues);
        }

        RunImpl run = new RunImpl(snapshot.runId(), pack, locale, snapshot.seed(), persistentInputs(snapshot.startInputs(), pack),
                snapshot.createdAt(), snapshot.updatedAt(), snapshot.step(), currentSceneId,
            new ArrayList<>(restoredState.visited()), restoredState.ended(), restoredState.endReason(),
            sceneSet, clock, rules, restoredState, placeMap, inputGuard, inputInterpreter, piiScrubber);
        return new ResumeResult(run, issues);
    }

    private RunState migrateState(RunState saved, Rules rules, SceneSet scenes, PlaceMap placeMap,
                                  boolean migrating, List<Issue> issues) {
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
        String currentPlaceId = saved.currentPlaceId();
        List<String> visitedPlaceIds = new ArrayList<>();
        if (placeMap == null) {
            if (currentPlaceId != null || !saved.visitedPlaces().isEmpty()) {
                if (migrating) pruned = true;
                else issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID",
                        "Snapshot contains place state for a pack without PLACES"));
            }
            currentPlaceId = null;
        } else {
            Set<String> placeIds = new HashSet<>();
            placeMap.places().forEach(place -> placeIds.add(place.id()));
            boolean missingCurrent = currentPlaceId == null;
            boolean unknownCurrent = currentPlaceId != null && !placeIds.contains(currentPlaceId);
            if (unknownCurrent) {
                if (migrating) {
                    currentPlaceId = placeMap.startPlace();
                    pruned = true;
                } else {
                    issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID",
                            "Snapshot references unknown current place: " + currentPlaceId));
                }
            } else if (missingCurrent || migrating && saved.visitedPlaces().isEmpty()) {
                currentPlaceId = placeMap.startPlace();
            }
            Set<String> seenPlaces = new HashSet<>();
            for (String visitedId : saved.visitedPlaces()) {
                if (!seenPlaces.add(visitedId)) {
                    if (migrating) pruned = true;
                    else issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID", "Snapshot visited-place list contains duplicates"));
                } else if (!placeIds.contains(visitedId)) {
                    if (migrating) pruned = true;
                    else issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID",
                            "Snapshot visited-place list references unknown place: " + visitedId));
                } else visitedPlaceIds.add(visitedId);
            }
            if (!visitedPlaceIds.contains(currentPlaceId)) {
                if (migrating || missingCurrent) {
                    visitedPlaceIds.add(currentPlaceId);
                    if (migrating) pruned = true;
                } else {
                    issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID",
                            "Current place is missing from visited-place list"));
                }
            }
        }
        if (pruned) issues.add(new Issue(Issue.Severity.WARNING, "SNAPSHOT_STATE_PRUNED", "Obsolete snapshot state was removed"));
        if (clamped) issues.add(new Issue(Issue.Severity.WARNING, "SNAPSHOT_STATE_CLAMPED", "Snapshot counters were clamped to current bounds"));
        return new RunState(saved.sceneId(), visited, saved.ended(), saved.endReason(), counters, flags, sets,
                objectives, outcome, currentPlaceId, visitedPlaceIds);
    }

    private PlaceMap placeMap(Pack pack) {
        if (!pack.manifest().features().contains(PackFeature.PLACES)) return null;
        PackId packId = pack.manifest().id();
        return placeMaps.computeIfAbsent(packId, ignored -> PlacesParser.parse(pack));
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
            if (input instanceof StartInputDef.FreeTextInput) {
                try {
                    String normalizedText = piiScrubber.normalize(value);
                    int maxChars = Math.min(freeTextLimit(input), PiiScrubber.MAX_INPUT_CODE_POINTS);
                    if (normalizedText.codePointCount(0, normalizedText.length()) > maxChars) {
                        issues.add(new Issue(Issue.Severity.ERROR, "INPUT_TOO_LONG", "Text input exceeds the allowed length"));
                        continue;
                    }
                } catch (PiiScrubber.InputTextException exception) {
                    issues.add(new Issue(Issue.Severity.ERROR, exception.code(), "Text input is invalid"));
                    continue;
                }
            } else if (!inputValueValid(input, value)) {
                issues.add(new Issue(Issue.Severity.ERROR, "START_INPUT_INVALID",
                        "Start input value is invalid for " + input.id()));
                continue;
            }
            if (input instanceof StartInputDef.FreeTextInput) {
                if (pack.manifest().contentPolicy() == null || !pack.manifest().contentPolicy().freeTextAllowed()) {
                    issues.add(new Issue(Issue.Severity.ERROR, "FREE_TEXT_DISABLED", "Free-text input is disabled by pack policy"));
                    continue;
                }
                if (inputGuard == null) {
                    issues.add(new Issue(Issue.Severity.ERROR, "INPUT_GUARD_UNAVAILABLE", "Text safety checks are unavailable"));
                    continue;
                }
                try {
                    ProcessedInput processed = piiScrubber.process(value);
                    InputSafetyDecision decision = inputGuard.inspect(processed.input(), policy(pack,
                            Math.min(freeTextLimit(input), PiiScrubber.MAX_INPUT_CODE_POINTS)));
                    if (decision == null || decision.action() == null || decision.action() == InputSafetyAction.UNAVAILABLE) {
                        issues.add(new Issue(Issue.Severity.ERROR, "INPUT_GUARD_UNAVAILABLE", "Text safety checks are unavailable"));
                    } else if (decision.action() == InputSafetyAction.BLOCK) {
                        issues.add(new Issue(Issue.Severity.ERROR, "INPUT_BLOCKED", "Text input was not accepted"));
                    } else if (decision.action() == InputSafetyAction.NEEDS_CLARIFICATION) {
                        issues.add(new Issue(Issue.Severity.ERROR, "INPUT_NEEDS_CLARIFICATION", "Text input needs clarification"));
                    }
                } catch (RuntimeException exception) {
                    issues.add(new Issue(Issue.Severity.ERROR, "INPUT_GUARD_UNAVAILABLE", "Text safety checks are unavailable"));
                }
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
        if (input instanceof StartInputDef.FreeTextInput) return true;
        return false;
    }

    private int freeTextLimit(StartInputDef input) {
        return input instanceof StartInputDef.FreeTextInput freeText ? freeText.maxChars() : PiiScrubber.DEFAULT_INPUT_CODE_POINTS;
    }

    private InputPolicy policy(Pack pack, int maxChars) {
        ContentPolicy contentPolicy = pack.manifest().contentPolicy();
        String audience = contentPolicy == null || contentPolicy.audience() == null ? null
                : contentPolicy.audience().minAge() + "-" + contentPolicy.audience().maxAge();
        Set<String> tags = contentPolicy == null ? Set.of() : Set.copyOf(contentPolicy.forbiddenTags());
        return new InputPolicy(contentPolicy != null && contentPolicy.freeTextAllowed(), maxChars, 280, 3,
                audience, tags, "1");
    }

    private Map<String, String> persistentInputs(Map<String, String> inputs, Pack pack) {
        Set<String> freeTextIds = new HashSet<>();
        for (StartInputDef input : pack.manifest().startInputs()) {
            if (input instanceof StartInputDef.FreeTextInput) freeTextIds.add(input.id());
        }
        Map<String, String> persistent = new LinkedHashMap<>(inputs);
        freeTextIds.forEach(persistent::remove);
        return persistent;
    }
}
