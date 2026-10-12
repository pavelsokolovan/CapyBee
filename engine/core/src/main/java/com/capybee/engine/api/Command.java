package com.capybee.engine.api;

public sealed interface Command permits Command.Choose, Command.Advance, Command.SubmitText, Command.Abandon {
    record Choose(String optionId) implements Command {}

    record Advance() implements Command {}

    record SubmitText(String text) implements Command {}

    record Abandon() implements Command {}
}
