package com.capybee.engine.api;

public interface Run {
    String id();

    View view();

    StepResult apply(Command command);

    RunSnapshot snapshot();
}
