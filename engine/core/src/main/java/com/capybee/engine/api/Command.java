package com.capybee.engine.api;

public sealed interface Command permits Command.Choose, Command.Advance, Command.SubmitText, Command.Abandon, Command.Move {
    record Choose(String optionId) implements Command {}

    record Advance() implements Command {}

    record SubmitText(String text) implements Command {}

    record Abandon() implements Command {}

    record Move(String placeId) implements Command {}
}
