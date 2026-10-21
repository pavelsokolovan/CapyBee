package com.capybee.engine.rules;

import java.util.List;

public record Rules(List<CounterDef> counters, List<FlagDef> flags, List<SetDef> sets,
                    List<ObjectiveDef> objectives, List<OutcomeDef> outcomes,
                    List<ResultFactDef> resultFacts) {
    public Rules(List<CounterDef> counters, List<FlagDef> flags, List<SetDef> sets,
                 List<ObjectiveDef> objectives, List<OutcomeDef> outcomes) {
        this(counters, flags, sets, objectives, outcomes, List.of());
    }
    public Rules {
        counters = List.copyOf(counters);
        flags = List.copyOf(flags);
        sets = List.copyOf(sets);
        objectives = List.copyOf(objectives);
        outcomes = List.copyOf(outcomes);
        resultFacts = List.copyOf(resultFacts);
    }

    public static Rules empty() {
        return new Rules(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
