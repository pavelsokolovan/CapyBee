package com.capybee.engine.content;

import com.capybee.engine.rules.Condition;

public record SceneBranch(Condition condition, String next) {}