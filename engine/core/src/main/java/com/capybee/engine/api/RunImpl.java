package com.capybee.engine.api;

import com.capybee.engine.content.Scene;
import com.capybee.engine.content.SceneOption;
import com.capybee.engine.content.SceneSet;
import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.Pack;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class RunImpl implements Run {
    private final String id;
    private final Pack pack;
    private final LocaleTag locale;
    private final long seed;
    private final Map<String, String> startInputs;
    private final Instant createdAt;
    private Instant updatedAt;
    private final Clock clock;
    private String sceneId;
    private final List<String> visited;
    private boolean ended;
    private EndReason endReason;
    private final SceneSet sceneSet;
    private long step;

    RunImpl(String id, Pack pack, LocaleTag locale, long seed, Map<String, String> startInputs,
            Instant createdAt, Instant updatedAt, long step, String sceneId, List<String> visited,
            boolean ended, EndReason endReason, SceneSet sceneSet, Clock clock) {
        this.id = id;
        this.pack = pack;
        this.locale = locale;
        this.seed = seed;
        this.startInputs = Map.copyOf(startInputs);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.step = step;
        this.sceneId = sceneId;
        this.visited = new ArrayList<>(visited);
        this.ended = ended;
        this.endReason = endReason;
        this.sceneSet = sceneSet;
        this.clock = clock;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public View view() {
        if (ended && endReason == EndReason.ABANDONED) {
            return ViewBuilder.build(null, true, EndReason.ABANDONED);
        }
        Scene scene = sceneSet.scenes().get(sceneId);
        return ViewBuilder.build(scene, ended, endReason);
    }

    @Override
    public StepResult apply(Command command) {
        if (ended) {
            return reject("RUN_ENDED", "The run has already ended");
        }

        Scene current = sceneSet.scenes().get(sceneId);
        if (current == null) {
            return reject("COMMAND_NOT_ALLOWED", "The current scene is missing");
        }

        if (command instanceof Command.Abandon) {
            ended = true;
            endReason = EndReason.ABANDONED;
            step += 1;
            updatedAt = clock.instant();
            return new StepResult(true, ViewBuilder.build(null, true, EndReason.ABANDONED), List.of(), List.of());
        }

        if (command instanceof Command.SubmitText) {
            return reject("INPUT_NOT_EXPECTED", "Text input is not expected in this scene");
        }

        if (command instanceof Command.Choose choose) {
            if (current.options() == null || current.options().isEmpty()) {
                return reject("COMMAND_NOT_ALLOWED", "Choice commands are not allowed in the current scene");
            }
            SceneOption option = current.options().stream()
                    .filter(item -> item.id().equals(choose.optionId()))
                    .findFirst()
                    .orElse(null);
            if (option == null) {
                return reject("OPTION_UNKNOWN", "Unknown option id: " + choose.optionId());
            }
            return advanceTo(option.next(), "Choose option " + option.id());
        }

        if (command instanceof Command.Advance) {
            if (current.options() != null && !current.options().isEmpty()) {
                return reject("COMMAND_NOT_ALLOWED", "Advance is not allowed in a choice scene");
            }
            if (current.next() == null || current.next().isBlank()) {
                return reject("COMMAND_NOT_ALLOWED", "There is no next scene to advance to");
            }
            return advanceTo(current.next(), "Advance to next scene");
        }

        return reject("COMMAND_NOT_ALLOWED", "Unsupported command for this scene");
    }

    @Override
    public RunSnapshot snapshot() {
        return new RunSnapshot(1, id, new PackRef(pack.manifest().id(), pack.manifest().version()),
                locale, seed, startInputs, createdAt, updatedAt, step,
                new RunState(sceneId, List.copyOf(visited), ended, endReason));
    }

    private StepResult advanceTo(String targetSceneId, String reason) {
        if (targetSceneId == null || targetSceneId.isBlank()) {
            return reject("COMMAND_NOT_ALLOWED", "The next scene id is missing");
        }
        Scene target = sceneSet.scenes().get(targetSceneId);
        if (target == null) {
            return reject("COMMAND_NOT_ALLOWED", "Target scene is missing: " + targetSceneId);
        }

        sceneId = targetSceneId;
        if (!visited.contains(targetSceneId)) {
            visited.add(targetSceneId);
        }
        if (target.end()) {
            ended = true;
            endReason = EndReason.COMPLETED;
        }
        step += 1;
        updatedAt = clock.instant();

        View newView = ViewBuilder.build(target, ended, endReason);
        return new StepResult(true, newView, List.of(), List.of());
    }

    private StepResult reject(String code, String message) {
        return new StepResult(false, view(), List.of(), List.of(new Issue(Issue.Severity.ERROR, code, message)));
    }
}
