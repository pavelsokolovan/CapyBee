package com.capybee.engine.port;

import com.capybee.engine.input.InputPolicy;
import com.capybee.engine.input.InputSafetyDecision;
import com.capybee.engine.input.SafeTextInput;

@FunctionalInterface
public interface InputGuard {
    InputSafetyDecision inspect(SafeTextInput input, InputPolicy policy);
}