package com.capybee.engine.generation.blueprint;

import com.capybee.engine.rules.Condition;
import com.capybee.engine.rules.Effect;
import com.capybee.engine.rules.GeneratedMechanicsV1;
import java.util.List;
import java.util.Objects;

/** Immutable, non-executable binding of a validated blueprint to generated-rules-v1. */
public final class ValidatedMechanicsPlan {
    private final String contractVersion;
    private final List<Condition> objectiveConditions;
    private final List<List<ChoiceMechanics>> sceneChoices;

    ValidatedMechanicsPlan(List<Condition> objectiveConditions, List<List<ChoiceMechanics>> sceneChoices) {
        this.contractVersion = GeneratedMechanicsV1.CONTRACT_VERSION;
        this.objectiveConditions = List.copyOf(objectiveConditions);
        this.sceneChoices = sceneChoices.stream().map(List::copyOf).toList();
    }

    public String contractVersion() { return contractVersion; }
    public List<Condition> objectiveConditions() { return objectiveConditions; }
    public List<List<ChoiceMechanics>> sceneChoices() { return sceneChoices; }

    @Override public String toString() { return "ValidatedMechanicsPlan[contractVersion=" + contractVersion + ", redacted]"; }

    public record ChoiceMechanics(Condition requires, List<Effect> effects) {
        public ChoiceMechanics { effects = List.copyOf(Objects.requireNonNull(effects)); }
        @Override public String toString() { return "ChoiceMechanics[redacted]"; }
    }
}
