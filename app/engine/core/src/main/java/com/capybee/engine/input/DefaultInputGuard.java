package com.capybee.engine.input;

import com.capybee.engine.port.InputGuard;

import java.util.Locale;
import java.util.Set;

/** Deterministic, fail-closed host guard for already-normalized and PII-scrubbed start text. */
public final class DefaultInputGuard implements InputGuard {
    public static final String POLICY_VERSION = "capybee-local-input-v1";
    private static final Set<String> BLOCKED = Set.of(
            "ignore previous", "ignore all", "system prompt", "developer message",
            "password", "api key", "secret key", "contact me", "exact address");

    @Override
    public InputSafetyDecision inspect(SafeTextInput input, InputPolicy policy) {
        if (input == null || policy == null || !policy.freeTextAllowed())
            return new InputSafetyDecision(InputSafetyAction.UNAVAILABLE, SafetyCategory.UNKNOWN, "INPUT_POLICY_UNAVAILABLE");
        String value = input.text().toLowerCase(Locale.ROOT);
        if (BLOCKED.stream().anyMatch(value::contains))
            return new InputSafetyDecision(InputSafetyAction.BLOCK, SafetyCategory.PROMPT_INJECTION_OR_POLICY_BYPASS, "INPUT_POLICY_BLOCKED");
        if (value.contains("[redacted:"))
            return new InputSafetyDecision(InputSafetyAction.ALLOW_REDACTED, SafetyCategory.PERSONAL_DATA, "INPUT_REDACTED");
        return new InputSafetyDecision(InputSafetyAction.ALLOW, SafetyCategory.UNKNOWN, "INPUT_APPROVED");
    }
}
