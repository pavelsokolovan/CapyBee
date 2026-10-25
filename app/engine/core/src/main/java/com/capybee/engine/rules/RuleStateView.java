package com.capybee.engine.rules;

import java.util.Set;

/** Read-only state required by the authored condition DSL. */
public interface RuleStateView {
    int counter(String id);
    boolean flag(String id);
    Set<String> set(String id);
    boolean visitedScene(String id);
    long step();
    boolean atPlace(String id);
    boolean visitedPlace(String id);
}
