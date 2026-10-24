package com.capybee.engine.generation.blueprint;

import com.capybee.engine.generation.PhaseKind;
import com.capybee.engine.rules.ResultClass;
import java.util.List;

/** Untrusted, shape-checked proposal. This type is neither playable nor executable. */
public record ParsedGameBlueprint(int schemaVersion, String locale, String title, String premise, String setting,
        List<String> toneTags, List<String> storyPromises, List<Actor> actors, State state,
        List<Objective> objectives, List<Phase> phases, String startSceneId, List<Scene> scenes,
        List<Outcome> outcomes) {
    public ParsedGameBlueprint {
        toneTags=List.copyOf(toneTags); storyPromises=List.copyOf(storyPromises); actors=List.copyOf(actors);
        objectives=List.copyOf(objectives); phases=List.copyOf(phases); scenes=List.copyOf(scenes); outcomes=List.copyOf(outcomes);
    }
    @Override public String toString() { return "ParsedGameBlueprint[redacted]"; }

    public record Actor(String id,String name,String role,String description) { @Override public String toString(){return "Actor[redacted]";} }
    public record State(List<Counter> counters,List<Flag> flags,List<FiniteSet> sets) {
        public State { counters=List.copyOf(counters); flags=List.copyOf(flags); sets=List.copyOf(sets); }
        @Override public String toString(){return "State[redacted]";}
    }
    public record Counter(String id,int min,int max,int initial) { @Override public String toString(){return "Counter[redacted]";} }
    public record Flag(String id,boolean initial) { @Override public String toString(){return "Flag[redacted]";} }
    public record FiniteSet(String id,List<String> values,List<String> initial) {
        public FiniteSet { values=List.copyOf(values); initial=List.copyOf(initial); }
        @Override public String toString(){return "FiniteSet[redacted]";}
    }
    public record Objective(String id,String title,String description,Condition completeWhen) { @Override public String toString(){return "Objective[redacted]";} }
    public record Phase(PhaseKind kind,String goal,String development,String transition,List<String> actorIds,List<String> objectiveIds) {
        public Phase { actorIds=List.copyOf(actorIds); objectiveIds=List.copyOf(objectiveIds); }
        @Override public String toString(){return "Phase[redacted]";}
    }
    public sealed interface Scene permits DecisionScene,EndingScene {}
    public record DecisionScene(String id,PhaseKind phase,String title,String intent,List<String> actorIds,List<String> objectiveIds,List<Choice> choices) implements Scene {
        public DecisionScene { actorIds=List.copyOf(actorIds); objectiveIds=List.copyOf(objectiveIds); choices=List.copyOf(choices); }
        @Override public String toString(){return "DecisionScene[redacted]";}
    }
    public record EndingScene(String id,PhaseKind phase,String title,String intent,List<String> actorIds,List<String> objectiveIds,String outcomeId) implements Scene {
        public EndingScene { actorIds=List.copyOf(actorIds); objectiveIds=List.copyOf(objectiveIds); }
        @Override public String toString(){return "EndingScene[redacted]";}
    }
    public record Choice(String id,String intent,String destinationSceneId,Condition requires,List<Effect> effects) {
        public Choice { effects=List.copyOf(effects); }
        @Override public String toString(){return "Choice[redacted]";}
    }
    public record Outcome(String id,ResultClass resultClass,String title,String description) { @Override public String toString(){return "Outcome[redacted]";} }

    public sealed interface Condition permits CounterCondition,FlagCondition,SetContainsCondition,SetSizeCondition,VisitedSceneCondition,CompositeCondition,NotCondition {}
    public record CounterCondition(String type,String counter,int value) implements Condition { @Override public String toString(){return "Condition[redacted]";} }
    public record FlagCondition(String flag,boolean value) implements Condition { @Override public String toString(){return "Condition[redacted]";} }
    public record SetContainsCondition(String set,String value) implements Condition { @Override public String toString(){return "Condition[redacted]";} }
    public record SetSizeCondition(String set,int size) implements Condition { @Override public String toString(){return "Condition[redacted]";} }
    public record VisitedSceneCondition(String scene) implements Condition { @Override public String toString(){return "Condition[redacted]";} }
    public record CompositeCondition(String type,List<Condition> conditions) implements Condition { public CompositeCondition{conditions=List.copyOf(conditions);} @Override public String toString(){return "Condition[redacted]";} }
    public record NotCondition(Condition condition) implements Condition { @Override public String toString(){return "Condition[redacted]";} }

    public sealed interface Effect permits CounterEffect,FlagEffect,SetEffect {}
    public record CounterEffect(String type,String counter,int value) implements Effect { @Override public String toString(){return "Effect[redacted]";} }
    public record FlagEffect(String flag,boolean value) implements Effect { @Override public String toString(){return "Effect[redacted]";} }
    public record SetEffect(String type,String set,String value) implements Effect { @Override public String toString(){return "Effect[redacted]";} }
}
