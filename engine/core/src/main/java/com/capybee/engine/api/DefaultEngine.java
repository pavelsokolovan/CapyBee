package com.capybee.engine.api;

import com.capybee.engine.adapter.Randoms;
import com.capybee.engine.content.Scene;
import com.capybee.engine.content.SceneSet;
import com.capybee.engine.content.json.SceneSets;
import com.capybee.engine.pack.*;
import com.capybee.engine.port.IdSource;

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
        RunImpl run = new RunImpl(runId, pack, locale, seed, normalizedInputs, now, now, 0L,
                startScene.id(), visited, startScene.end(), startScene.end() ? EndReason.COMPLETED : null,
                sceneSet, clock);
        return new StartResult(run, issues);
    }

    @Override
    public ResumeResult resume(RunSnapshot snapshot) {
        List<Issue> issues = new ArrayList<>();
        if (snapshot == null) {
            issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID", "Snapshot is missing"));
            return new ResumeResult(null, issues);
        }

        Pack pack = packs.find(snapshot.pack().id()).orElse(null);
        if (pack == null) {
            issues.add(new Issue(Issue.Severity.ERROR, "PACK_NOT_FOUND", "Pack not found: " + snapshot.pack().id()));
            return new ResumeResult(null, issues);
        }
        if (!pack.manifest().version().equals(snapshot.pack().version())) {
            issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_PACK_MISMATCH",
                    "Snapshot pack version does not match loaded pack version"));
            return new ResumeResult(null, issues);
        }

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
        for (String visitedId : snapshot.state().visited()) {
            if (!sceneSet.scenes().containsKey(visitedId)) {
                issues.add(new Issue(Issue.Severity.ERROR, "SNAPSHOT_INVALID",
                        "Snapshot visited list references unknown scene: " + visitedId));
            }
        }
        if (issues.stream().anyMatch(issue -> issue.severity() == Issue.Severity.ERROR)) {
            return new ResumeResult(null, issues);
        }

        RunImpl run = new RunImpl(snapshot.runId(), pack, locale, snapshot.seed(), snapshot.startInputs(),
                snapshot.createdAt(), snapshot.updatedAt(), snapshot.step(), currentSceneId,
                new ArrayList<>(snapshot.state().visited()), snapshot.state().ended(), snapshot.state().endReason(),
                sceneSet, clock);
        return new ResumeResult(run, issues);
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
