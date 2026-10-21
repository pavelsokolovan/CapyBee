package com.capybee.engine.api;

import com.capybee.engine.rules.ResultClass;
import java.util.List;

public record RunResult(String outcomeId, String label, ResultClass resultClass,
                        String headline, String summary, List<ObjectiveResultItem> objectives,
                        List<ResultFactItem> facts, String reflection) {
    public RunResult {
        objectives = List.copyOf(objectives);
        facts = List.copyOf(facts);
    }
}
