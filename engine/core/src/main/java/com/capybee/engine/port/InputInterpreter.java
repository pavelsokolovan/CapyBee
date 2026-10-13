package com.capybee.engine.port;

import com.capybee.engine.input.InputContext;
import com.capybee.engine.input.InputResolution;
import com.capybee.engine.input.SafeTextInput;

@FunctionalInterface
public interface InputInterpreter {
    InputResolution interpret(SafeTextInput input, InputContext context);
}