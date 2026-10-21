package com.capybee.engine.cli;

sealed interface ConsoleIntent {
    record ChooseIndex(int oneBasedIndex) implements ConsoleIntent {}
    record ChooseId(String optionId) implements ConsoleIntent {}
    record Advance() implements ConsoleIntent {}
    record SubmitText(String text) implements ConsoleIntent {}
    record Move(String placeId) implements ConsoleIntent {}
    record Utility(UtilityCommand command, String argument) implements ConsoleIntent {}
    record Invalid(String code) implements ConsoleIntent {}

    enum UtilityCommand {
        HELP, LOOK, STATUS, OBJECTIVES, CHARACTERS, MAP, SAVE, QUIT, ABANDON,
        EVENTS, DEBUG, DIAGNOSTICS
    }
}
