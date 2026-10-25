package com.capybee.engine.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GeneratedMechanicsV1Test {
    @Test void registryIsClosedVersionedAndHasCompleteMetadata() {
        assertThat(GeneratedMechanicsV1.CONTRACT_VERSION).isEqualTo("generated-rules-v1");
        assertThat(GeneratedMechanicsV1.conditionNames()).hasSize(10).containsExactly(
                "COUNTER_AT_LEAST", "COUNTER_AT_MOST", "COUNTER_EQUALS", "FLAG_IS", "SET_CONTAINS",
                "SET_SIZE_AT_LEAST", "VISITED_SCENE", "ALL", "ANY", "NOT");
        assertThat(GeneratedMechanicsV1.effectNames()).hasSize(5).containsExactly(
                "COUNTER_ADD", "COUNTER_SET", "FLAG_SET", "SET_ADD", "SET_REMOVE");
        assertThat(GeneratedMechanicsV1.Operator.values()).extracting(GeneratedMechanicsV1.Operator::stateKind)
                .contains(GeneratedMechanicsV1.StateKind.NONE, GeneratedMechanicsV1.StateKind.COUNTER,
                        GeneratedMechanicsV1.StateKind.FLAG, GeneratedMechanicsV1.StateKind.SET,
                        GeneratedMechanicsV1.StateKind.SET_MEMBER, GeneratedMechanicsV1.StateKind.SCENE);
        assertThat(GeneratedMechanicsV1.Operator.ALL.childShape()).isEqualTo(GeneratedMechanicsV1.ChildShape.MANY);
        assertThat(GeneratedMechanicsV1.Operator.NOT.childShape()).isEqualTo(GeneratedMechanicsV1.ChildShape.ONE);
        assertThat(GeneratedMechanicsV1.operator("SCRIPT")).isNull();
        assertThat(GeneratedMechanicsV1.operator(null)).isNull();
        assertThatThrownBy(() -> GeneratedMechanicsV1.conditionNames().add("SCRIPT"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void generatedMappingsExcludeEveryAuthoredOnlyVariant() {
        List<Condition> conditions = List.of(new Condition.CounterAtLeast("c", 1), new Condition.CounterAtMost("c", 1),
                new Condition.CounterEquals("c", 1), new Condition.FlagIs("f", true), new Condition.SetContains("s", "v"),
                new Condition.SetSizeAtLeast("s", 1), new Condition.VisitedScene("x"), new Condition.All(List.of()),
                new Condition.Any(List.of()), new Condition.Not(new Condition.FlagIs("f", false)));
        assertThat(conditions).extracting(GeneratedMechanicsV1::nameOf)
                .containsExactlyElementsOf(GeneratedMechanicsV1.conditionNames());
        assertThat(List.of(new Effect.CounterAdd("c", 1), new Effect.CounterSet("c", 1), new Effect.FlagSet("f", true),
                new Effect.SetAdd("s", "v"), new Effect.SetRemove("s", "v")))
                .extracting(GeneratedMechanicsV1::nameOf).containsExactlyElementsOf(GeneratedMechanicsV1.effectNames());
        assertThat(GeneratedMechanicsV1.nameOf(new Condition.StepAtLeast(1))).isNull();
        assertThat(GeneratedMechanicsV1.nameOf(new Condition.AtPlace("p"))).isNull();
        assertThat(GeneratedMechanicsV1.nameOf(new Condition.VisitedPlace("p"))).isNull();
        assertThat(GeneratedMechanicsV1.nameOf(new Effect.EndRun("o"))).isNull();
        assertThat(GeneratedMechanicsV1.nameOf(new Effect.GoTo("p"))).isNull();
        assertThat(GeneratedMechanicsV1.nameOf(new Effect.EmitEvent("e", Map.of()))).isNull();
    }

    @Test void sharedEvaluatorCoversAuthoredAndGeneratedTruthTablesWithShortCircuiting() {
        State state = new State();
        state.counters.put("c", 2); state.flags.put("f", true); state.sets.put("s", new LinkedHashSet<>(Set.of("v")));
        state.visitedScenes.add("scene"); state.currentPlace = "place"; state.visitedPlaces.add("place"); state.step = 3;
        assertThat(ConditionEvaluator.evaluate(new Condition.All(List.of(
                new Condition.CounterAtLeast("c", 2), new Condition.CounterAtMost("c", 2),
                new Condition.CounterEquals("c", 2), new Condition.FlagIs("f", true),
                new Condition.SetContains("s", "v"), new Condition.SetSizeAtLeast("s", 1),
                new Condition.VisitedScene("scene"), new Condition.AtPlace("place"),
                new Condition.VisitedPlace("place"), new Condition.StepAtLeast(3),
                new Condition.Not(new Condition.FlagIs("f", false)))), state)).isTrue();
        assertThat(ConditionEvaluator.evaluate(new Condition.Any(List.of(
                new Condition.FlagIs("f", false), new Condition.CounterEquals("c", 2))), state)).isTrue();
        RuleStateView failOnRead = new State() { @Override public int counter(String id) { throw new AssertionError("not short-circuited"); } };
        assertThat(ConditionEvaluator.evaluate(new Condition.All(List.of(
                new Condition.FlagIs("missing", true), new Condition.CounterEquals("c", 2))), failOnRead)).isFalse();
        assertThat(ConditionEvaluator.evaluate(new Condition.Any(List.of(
                new Condition.FlagIs("missing", false), new Condition.CounterEquals("c", 2))), failOnRead)).isTrue();
    }

    @Test void sharedMutationRejectsGeneratedOverflowAndClampsAuthoredOverflow() {
        State state = new State(); state.counters.put("c", 3); state.bounds.put("c", new int[]{0,3});
        assertThat(StateEffectSemantics.apply(new Effect.CounterAdd("c", 1), state,
                StateEffectSemantics.CounterPolicy.REJECT_OUT_OF_RANGE))
                .isEqualTo(StateEffectSemantics.Failure.COUNTER_OUT_OF_RANGE);
        assertThat(state.counters).containsEntry("c", 3);
        assertThat(StateEffectSemantics.apply(new Effect.CounterAdd("c", Integer.MAX_VALUE), state,
                StateEffectSemantics.CounterPolicy.CLAMP_TO_DECLARATION)).isEqualTo(StateEffectSemantics.Failure.NONE);
        assertThat(state.counters).containsEntry("c", 3);
        state.counters.put("c", 0);
        StateEffectSemantics.apply(new Effect.CounterAdd("c", -1), state, StateEffectSemantics.CounterPolicy.CLAMP_TO_DECLARATION);
        assertThat(state.counters).containsEntry("c", 0);
        assertThat(StateEffectSemantics.apply(new Effect.CounterSet("c", 2), state,
                StateEffectSemantics.CounterPolicy.REJECT_OUT_OF_RANGE)).isEqualTo(StateEffectSemantics.Failure.NONE);
        assertThat(state.counters).containsEntry("c", 2);
    }

    @Test void sharedMutationPreservesFlagAndIdempotentFiniteSetSemanticsAndFailsClosed() {
        State state = new State(); state.flags.put("f", false); state.setMembers.put("s", Set.of("v"));
        state.sets.put("s", new LinkedHashSet<>());
        assertThat(StateEffectSemantics.apply(new Effect.FlagSet("f", true), state,
                StateEffectSemantics.CounterPolicy.REJECT_OUT_OF_RANGE)).isEqualTo(StateEffectSemantics.Failure.NONE);
        StateEffectSemantics.apply(new Effect.SetAdd("s", "v"), state, StateEffectSemantics.CounterPolicy.REJECT_OUT_OF_RANGE);
        StateEffectSemantics.apply(new Effect.SetAdd("s", "v"), state, StateEffectSemantics.CounterPolicy.REJECT_OUT_OF_RANGE);
        assertThat(state.sets.get("s")).containsExactly("v");
        StateEffectSemantics.apply(new Effect.SetRemove("s", "v"), state, StateEffectSemantics.CounterPolicy.REJECT_OUT_OF_RANGE);
        StateEffectSemantics.apply(new Effect.SetRemove("s", "v"), state, StateEffectSemantics.CounterPolicy.REJECT_OUT_OF_RANGE);
        assertThat(state.sets.get("s")).isEmpty();
        assertThat(StateEffectSemantics.apply(new Effect.FlagSet("missing", true), state,
                StateEffectSemantics.CounterPolicy.REJECT_OUT_OF_RANGE)).isEqualTo(StateEffectSemantics.Failure.UNKNOWN_TARGET);
        assertThat(StateEffectSemantics.apply(new Effect.SetAdd("s", "private"), state,
                StateEffectSemantics.CounterPolicy.REJECT_OUT_OF_RANGE)).isEqualTo(StateEffectSemantics.Failure.UNKNOWN_SET_MEMBER);
        assertThat(StateEffectSemantics.apply(new Effect.EndRun(null), state,
                StateEffectSemantics.CounterPolicy.REJECT_OUT_OF_RANGE)).isEqualTo(StateEffectSemantics.Failure.UNSUPPORTED_EFFECT);
    }

    private static class State implements RuleStateView, StateEffectSemantics.StateMutation {
        final Map<String,Integer> counters=new LinkedHashMap<>(); final Map<String,int[]> bounds=new LinkedHashMap<>();
        final Map<String,Boolean> flags=new LinkedHashMap<>(); final Map<String,LinkedHashSet<String>> sets=new LinkedHashMap<>();
        final Map<String,Set<String>> setMembers=new LinkedHashMap<>(); final Set<String> visitedScenes=new LinkedHashSet<>();
        final Set<String> visitedPlaces=new LinkedHashSet<>(); String currentPlace; long step;
        public int counter(String id){return counters.getOrDefault(id,0);} public Integer counterMin(String id){return bounds.containsKey(id)?bounds.get(id)[0]:null;}
        public Integer counterValue(String id){return counters.get(id);}
        public Integer counterMax(String id){return bounds.containsKey(id)?bounds.get(id)[1]:null;} public boolean flag(String id){return flags.getOrDefault(id,false);}
        public Set<String> set(String id){return sets.getOrDefault(id,new LinkedHashSet<>());} public boolean visitedScene(String id){return visitedScenes.contains(id);}
        public long step(){return step;} public boolean atPlace(String id){return id.equals(currentPlace);} public boolean visitedPlace(String id){return visitedPlaces.contains(id);}
        public Boolean flagValue(String id){return flags.get(id);}
        public boolean hasSet(String id){return sets.containsKey(id);} public boolean isSetMember(String id,String value){return setMembers.getOrDefault(id,Set.of()).contains(value);}
        public void setCounter(String id,int value){counters.put(id,value);} public void setFlag(String id,boolean value){flags.put(id,value);}
        public void addSetValue(String id,String value){sets.get(id).add(value);} public void removeSetValue(String id,String value){sets.get(id).remove(value);}
    }
}
