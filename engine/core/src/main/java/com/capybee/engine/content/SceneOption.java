package com.capybee.engine.content;

import com.capybee.engine.rules.Condition;
import com.capybee.engine.rules.Effect;

import java.util.List;

public record SceneOption(String id, String label, String next, List<Effect> effects,
						  Condition requires, String unmetHint, boolean hideWhenUnmet) {
	public SceneOption {
		effects = effects == null ? List.of() : List.copyOf(effects);
	}

	public SceneOption(String id, String label, String next) {
		this(id, label, next, List.of(), null, null, false);
	}
}
