package com.capybee.engine.api;

import com.capybee.engine.content.Scene;
import com.capybee.engine.content.SceneBranch;
import com.capybee.engine.content.SceneOption;
import com.capybee.engine.content.SceneSet;
import com.capybee.engine.pack.EventDef;
import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.Pack;
import com.capybee.engine.pack.PackFeature;
import com.capybee.engine.rules.Condition;
import com.capybee.engine.rules.CounterDef;
import com.capybee.engine.rules.Effect;
import com.capybee.engine.rules.FlagDef;
import com.capybee.engine.rules.ObjectiveDef;
import com.capybee.engine.rules.Rules;
import com.capybee.engine.rules.SetDef;
import com.capybee.engine.rules.json.RuleStrings;
import com.capybee.engine.places.PlaceDef;
import com.capybee.engine.places.PlaceLinkDef;
import com.capybee.engine.places.PlaceMap;
import com.capybee.engine.places.json.PlacesParser;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    private final SceneSet sceneSet;
    private final Rules rules;
    private final PlaceMap placeMap;
    private final Map<String, String> strings;
    private final Map<String, CounterDef> counterDefs = new LinkedHashMap<>();
    private final Map<String, FlagDef> flagDefs = new LinkedHashMap<>();
    private final Map<String, SetDef> setDefs = new LinkedHashMap<>();
    private final Map<String, Integer> counters = new LinkedHashMap<>();
    private final Map<String, Boolean> flags = new LinkedHashMap<>();
    private final Map<String, LinkedHashSet<String>> sets = new LinkedHashMap<>();
    private final Map<String, ObjectiveState> objectives = new LinkedHashMap<>();
    private final List<String> visited;
    private final List<String> visitedPlaces = new ArrayList<>();
    private final List<Event> startupEvents = new ArrayList<>();
    private final List<Issue> startupIssues = new ArrayList<>();
    private String sceneId;
    private String currentPlaceId;
    private boolean ended;
    private EndReason endReason;
    private String outcome;
    private boolean endRequested;
    private String requestedOutcome;
    private long step;

    RunImpl(String id, Pack pack, LocaleTag locale, long seed, Map<String, String> startInputs,
            Instant createdAt, Instant updatedAt, long step, String sceneId, List<String> visited,
            boolean ended, EndReason endReason, SceneSet sceneSet, Clock clock) {
        this(id, pack, locale, seed, startInputs, createdAt, updatedAt, step, sceneId, visited,
                ended, endReason, sceneSet, clock, Rules.empty(), null);
    }

    RunImpl(String id, Pack pack, LocaleTag locale, long seed, Map<String, String> startInputs,
            Instant createdAt, Instant updatedAt, long step, String sceneId, List<String> visited,
            boolean ended, EndReason endReason, SceneSet sceneSet, Clock clock, Rules rules,
            RunState savedState) {
        this(id, pack, locale, seed, startInputs, createdAt, updatedAt, step, sceneId, visited,
            ended, endReason, sceneSet, clock, rules, savedState,
            pack.manifest().features().contains(PackFeature.PLACES) ? PlacesParser.parse(pack) : null);
        }

        RunImpl(String id, Pack pack, LocaleTag locale, long seed, Map<String, String> startInputs,
            Instant createdAt, Instant updatedAt, long step, String sceneId, List<String> visited,
            boolean ended, EndReason endReason, SceneSet sceneSet, Clock clock, Rules rules,
            RunState savedState, PlaceMap placeMap) {
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
        this.rules = rules;
        this.placeMap = placeMap;
        this.strings = RuleStrings.load(pack, locale);
        rules.counters().forEach(def -> counterDefs.put(def.id(), def));
        rules.flags().forEach(def -> flagDefs.put(def.id(), def));
        rules.sets().forEach(def -> setDefs.put(def.id(), def));
        if (savedState == null) {
            rules.counters().forEach(def -> counters.put(def.id(), def.initial()));
            rules.flags().forEach(def -> flags.put(def.id(), def.initial()));
            rules.sets().forEach(def -> sets.put(def.id(), new LinkedHashSet<>(def.initial())));
            rules.objectives().forEach(def -> objectives.put(def.id(), ObjectiveState.ACTIVE));
            if (placeMap != null) {
                currentPlaceId = placeMap.startPlace();
                visitedPlaces.add(currentPlaceId);
            }
            initializeAtStart();
        } else {
            counters.putAll(savedState.counters());
            flags.putAll(savedState.flags());
            savedState.sets().forEach((setId, members) -> sets.put(setId, new LinkedHashSet<>(members)));
            objectives.putAll(savedState.objectives());
            outcome = savedState.outcome();
            currentPlaceId = savedState.currentPlaceId();
            visitedPlaces.addAll(savedState.visitedPlaces());
        }
    }

    List<Event> startupEvents() { return List.copyOf(startupEvents); }
    List<Issue> startupIssues() { return List.copyOf(startupIssues); }

    @Override
    public String id() { return id; }

    @Override
    public View view() {
        if (ended && endReason == EndReason.ABANDONED) {
            return ViewBuilder.build(null, true, endReason, List.of(), List.of(), List.of(), null, buildPlacesView(true));
        }
        return buildView(sceneSet.scenes().get(sceneId));
    }

    @Override
    public StepResult apply(Command command) {
        if (ended) return reject("RUN_ENDED", "The run has already ended");
        if (command instanceof Command.Abandon) {
            ended = true;
            endReason = EndReason.ABANDONED;
            step++;
            updatedAt = clock.instant();
            return new StepResult(true, view(), List.of(), List.of());
        }
        if (command instanceof Command.Move move) return move(move.placeId());
        Scene current = sceneSet.scenes().get(sceneId);
        if (current == null) return reject("COMMAND_NOT_ALLOWED", "The current scene is missing");
        if (command instanceof Command.SubmitText) return reject("INPUT_NOT_EXPECTED", "Text input is not expected in this scene");
        if (command instanceof Command.Choose choose) {
            if (current.options().isEmpty()) return reject("COMMAND_NOT_ALLOWED", "Choice commands are not allowed in the current scene");
            SceneOption option = current.options().stream().filter(item -> item.id().equals(choose.optionId())).findFirst().orElse(null);
            if (option == null) return reject("OPTION_UNKNOWN", "Unknown option id: " + choose.optionId());
            if (!isEnabled(option)) return reject("OPTION_DISABLED", "Option is unavailable: " + option.id());
            step++;
            List<Event> events = new ArrayList<>();
            List<Issue> issues = new ArrayList<>();
            applyEffects(option.effects(), events);
            enterTarget(option.next(), events, issues);
            updatedAt = clock.instant();
            return new StepResult(true, view(), events, issues);
        }
        if (command instanceof Command.Advance) {
            if (!current.options().isEmpty()) return reject("COMMAND_NOT_ALLOWED", "Advance is not allowed in a choice scene");
            if (current.next() == null || current.next().isBlank()) return reject("COMMAND_NOT_ALLOWED", "There is no next scene to advance to");
            step++;
            String target = current.next();
            for (SceneBranch branch : current.branches()) {
                if (evaluate(branch.condition())) {
                    target = branch.next();
                    break;
                }
            }
            List<Event> events = new ArrayList<>();
            List<Issue> issues = new ArrayList<>();
            enterTarget(target, events, issues);
            updatedAt = clock.instant();
            return new StepResult(true, view(), events, issues);
        }
        return reject("COMMAND_NOT_ALLOWED", "Unsupported command for this scene");
    }

    @Override
    public RunSnapshot snapshot() {
        Map<String, List<String>> savedSets = new LinkedHashMap<>();
        sets.forEach((setId, members) -> savedSets.put(setId, members.stream().sorted().toList()));
        return new RunSnapshot(1, id, new PackRef(pack.manifest().id(), pack.manifest().version()),
                locale, seed, startInputs, createdAt, updatedAt, step,
                new RunState(sceneId, List.copyOf(visited), ended, endReason,
                    counters, flags, savedSets, objectives, outcome, currentPlaceId, visitedPlaces));
    }

    private void initializeAtStart() {
        Scene start = sceneSet.scenes().get(sceneId);
        applyEffects(start.onEnter(), startupEvents);
        evaluateObjectives(startupEvents, startupIssues);
        if (start.end() || endRequested) finish(EndReason.COMPLETED, start);
    }

    private void enterTarget(String targetId, List<Event> events, List<Issue> issues) {
        Scene target = sceneSet.scenes().get(targetId);
        if (target == null) {
            issues.add(new Issue(Issue.Severity.ERROR, "COMMAND_NOT_ALLOWED", "Target scene is missing: " + targetId));
            return;
        }
        sceneId = targetId;
        if (!visited.contains(targetId)) visited.add(targetId);
        applyEffects(target.onEnter(), events);
        evaluateObjectives(events, issues);
        if (target.end() || endRequested) finish(EndReason.COMPLETED, target);
    }

    private void finish(EndReason reason, Scene target) {
        ended = true;
        endReason = reason;
        if (requestedOutcome != null) outcome = requestedOutcome;
        else if (target != null && target.outcome() != null) outcome = target.outcome();
        endRequested = false;
        requestedOutcome = null;
    }

    private void evaluateObjectives(List<Event> events, List<Issue> issues) {
        for (int pass = 0; pass < rules.objectives().size() + 1; pass++) {
            boolean changed = false;
            for (ObjectiveDef definition : rules.objectives()) {
                if (objectives.get(definition.id()) == ObjectiveState.ACTIVE && evaluate(definition.completeWhen())) {
                    objectives.put(definition.id(), ObjectiveState.DONE);
                    changed = true;
                    applyEffects(definition.onComplete(), events);
                }
            }
            if (!changed) return;
        }
        issues.add(new Issue(Issue.Severity.ERROR, "OBJECTIVE_EVALUATION_LIMIT", "Objective evaluation exceeded its fixed-point limit"));
        ended = true;
        endReason = EndReason.COMPLETED;
    }

    private boolean evaluate(Condition condition) {
        if (condition instanceof Condition.CounterAtLeast item) return counters.getOrDefault(item.counter(), 0) >= item.value();
        if (condition instanceof Condition.CounterAtMost item) return counters.getOrDefault(item.counter(), 0) <= item.value();
        if (condition instanceof Condition.CounterEquals item) return counters.getOrDefault(item.counter(), 0) == item.value();
        if (condition instanceof Condition.FlagIs item) return flags.getOrDefault(item.flag(), false) == item.value();
        if (condition instanceof Condition.SetContains item) return sets.getOrDefault(item.set(), new LinkedHashSet<>()).contains(item.value());
        if (condition instanceof Condition.SetSizeAtLeast item) return sets.getOrDefault(item.set(), new LinkedHashSet<>()).size() >= item.size();
        if (condition instanceof Condition.VisitedScene item) return visited.contains(item.scene());
        if (condition instanceof Condition.AtPlace item) return item.place().equals(currentPlaceId);
        if (condition instanceof Condition.VisitedPlace item) return visitedPlaces.contains(item.place());
        if (condition instanceof Condition.StepAtLeast item) return step >= item.step();
        if (condition instanceof Condition.All item) return item.conditions().stream().allMatch(this::evaluate);
        if (condition instanceof Condition.Any item) return item.conditions().stream().anyMatch(this::evaluate);
        if (condition instanceof Condition.Not item) return !evaluate(item.condition());
        return false;
    }

    private void applyEffects(List<Effect> effects, List<Event> events) {
        for (Effect effect : effects) {
            if (effect instanceof Effect.CounterAdd item) {
                CounterDef def = counterDefs.get(item.counter());
                if (def != null) setCounter(def, (long) counters.getOrDefault(item.counter(), def.initial()) + item.delta());
            } else if (effect instanceof Effect.CounterSet item) {
                CounterDef def = counterDefs.get(item.counter());
                if (def != null) setCounter(def, item.value());
            } else if (effect instanceof Effect.FlagSet item) {
                if (flagDefs.containsKey(item.flag())) flags.put(item.flag(), item.value());
            } else if (effect instanceof Effect.SetAdd item) {
                SetDef def = setDefs.get(item.set());
                if (def != null && def.values().contains(item.value())) sets.computeIfAbsent(item.set(), key -> new LinkedHashSet<>()).add(item.value());
            } else if (effect instanceof Effect.SetRemove item) {
                sets.computeIfAbsent(item.set(), key -> new LinkedHashSet<>()).remove(item.value());
            } else if (effect instanceof Effect.EmitEvent item) {
                events.add(toEvent(item));
            } else if (effect instanceof Effect.EndRun item) {
                endRequested = true;
                if (item.outcome() != null) requestedOutcome = item.outcome();
            } else if (effect instanceof Effect.GoTo item) {
                currentPlaceId = item.place();
                if (!visitedPlaces.contains(item.place())) visitedPlaces.add(item.place());
            }
        }
    }

    private Event toEvent(Effect.EmitEvent effect) {
        Map<String, String> payload = new LinkedHashMap<>();
        EventDef eventDef = pack.manifest().events().stream().filter(item -> item.id().equals(effect.event())).findFirst().orElse(null);
        if (eventDef == null) return new Event(effect.event(), Map.of());
        for (EventDef.PayloadField field : eventDef.payload()) {
            Effect.PayloadValue value = effect.payload().get(field.name());
            if (value instanceof Effect.PayloadValue.StringLiteral literal) payload.put(field.name(), literal.value());
            else if (value instanceof Effect.PayloadValue.IntegerLiteral literal) payload.put(field.name(), Integer.toString(literal.value()));
            else if (value instanceof Effect.PayloadValue.BooleanLiteral literal) payload.put(field.name(), Boolean.toString(literal.value()));
            else if (value instanceof Effect.PayloadValue.CounterRef reference) payload.put(field.name(), Integer.toString(counters.getOrDefault(reference.counter(), 0)));
        }
        return new Event(effect.event(), payload);
    }

    private void setCounter(CounterDef def, long value) {
        counters.put(def.id(), (int) Math.max(def.min(), Math.min((long) def.max(), value)));
    }

    private boolean isEnabled(SceneOption option) {
        return option.requires() == null || evaluate(option.requires());
    }

    private View buildView(Scene scene) {
        if (scene == null) return ViewBuilder.build(null, ended, endReason);
        List<StatusItem> status = new ArrayList<>();
        for (CounterDef def : rules.counters()) {
            if (def.display()) status.add(new StatusItem(def.id(), resolve(def.labelKey()),
                    counters.getOrDefault(def.id(), def.initial()) + " / " + def.max()));
        }
        List<ObjectiveItem> objectiveItems = new ArrayList<>();
        for (ObjectiveDef def : rules.objectives()) {
            objectiveItems.add(new ObjectiveItem(def.id(), resolve(def.labelKey()),
                    objectives.getOrDefault(def.id(), ObjectiveState.ACTIVE)));
        }
        List<Option> options = new ArrayList<>();
        if (!ended) {
            for (SceneOption option : scene.options()) {
                boolean enabled = isEnabled(option);
                if (!enabled && option.hideWhenUnmet()) continue;
                String hint = !enabled && option.unmetHint() != null ? resolve(option.unmetHint()) : null;
                options.add(new Option(option.id(), option.label(), enabled, hint));
            }
        }
        return ViewBuilder.build(scene, ended, endReason, options, status, objectiveItems, outcome,
                buildPlacesView(ended));
    }

    private StepResult move(String placeId) {
        if (placeMap == null) return reject("PLACES_NOT_ENABLED", "Places are not enabled for this run");
        if (placeMap.places().stream().noneMatch(place -> place.id().equals(placeId))) {
            return reject("PLACE_UNKNOWN", "Unknown place id: " + placeId);
        }
        if (!isConnected(currentPlaceId, placeId)) {
            return reject("PLACE_NOT_CONNECTED", "Place is not directly connected: " + placeId);
        }
        step++;
        currentPlaceId = placeId;
        if (!visitedPlaces.contains(placeId)) visitedPlaces.add(placeId);
        List<Event> events = new ArrayList<>();
        List<Issue> issues = new ArrayList<>();
        evaluateObjectives(events, issues);
        if (endRequested) finish(EndReason.COMPLETED, sceneSet.scenes().get(sceneId));
        updatedAt = clock.instant();
        return new StepResult(true, view(), events, issues);
    }

    private boolean isConnected(String from, String to) {
        for (PlaceLinkDef link : placeMap.links()) {
            if (link.from().equals(from) && link.to().equals(to)) return true;
            if (link.bidirectional() && link.from().equals(to) && link.to().equals(from)) return true;
        }
        return false;
    }

    private PlacesView buildPlacesView(boolean runEnded) {
        if (placeMap == null) return null;
        List<PlaceView> placeViews = new ArrayList<>();
        for (PlaceDef place : placeMap.places()) {
            String description = place.descriptionKey() == null ? null : resolve(place.descriptionKey());
            Integer x = place.position() == null ? null : place.position().x();
            Integer y = place.position() == null ? null : place.position().y();
            placeViews.add(new PlaceView(place.id(), resolve(place.labelKey()), description, x, y));
        }
        List<PlaceLinkView> links = placeMap.links().stream()
                .map(link -> new PlaceLinkView(link.from(), link.to(), link.bidirectional())).toList();
        List<String> availableMoves = new ArrayList<>();
        if (!runEnded) {
            for (PlaceDef place : placeMap.places()) {
                if (!place.id().equals(currentPlaceId) && isConnected(currentPlaceId, place.id())) {
                    availableMoves.add(place.id());
                }
            }
        }
        return new PlacesView(currentPlaceId, placeViews, links, availableMoves, visitedPlaces,
                placeMap.layoutKind().name());
    }

    private String resolve(String key) { return strings.getOrDefault(key, key); }

    private StepResult reject(String code, String message) {
        return new StepResult(false, view(), List.of(), List.of(new Issue(Issue.Severity.ERROR, code, message)));
    }
}
