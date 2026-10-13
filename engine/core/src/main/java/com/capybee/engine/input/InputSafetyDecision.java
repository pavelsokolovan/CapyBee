package com.capybee.engine.input;

public record InputSafetyDecision(InputSafetyAction action, SafetyCategory category, String reasonCode) {
	public InputSafetyDecision {
		if (reasonCode != null && !reasonCode.matches("[A-Z][A-Z0-9_]{0,47}")) {
			throw new IllegalArgumentException("Invalid safety reason code");
		}
	}
}