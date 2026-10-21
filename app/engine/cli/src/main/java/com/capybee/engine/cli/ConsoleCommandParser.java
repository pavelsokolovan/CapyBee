package com.capybee.engine.cli;

import com.capybee.engine.api.InputMode;
import com.capybee.engine.api.View;

import java.util.Locale;

final class ConsoleCommandParser {
    ConsoleIntent parse(String line, View view, boolean scripted, boolean developerMode) {
        if (line == null) return new ConsoleIntent.Utility(ConsoleIntent.UtilityCommand.QUIT, "");
        String trimmed = line.trim();
        if (trimmed.startsWith("/")) {
            ConsoleIntent intent = utility(trimmed, scripted, developerMode);
            if (intent instanceof ConsoleIntent.Move move && (view == null || view.places() == null
                    || !view.places().availableMoves().contains(move.placeId()))) {
                return new ConsoleIntent.Invalid("MOVE_NOT_AVAILABLE");
            }
            if (intent instanceof ConsoleIntent.SubmitText && view != null && view.input() != null
                    && view.input().mode() != InputMode.TEXT && view.input().mode() != InputMode.CHOICE_OR_TEXT) {
                return new ConsoleIntent.Invalid("TEXT_NOT_ALLOWED");
            }
            return intent;
        }
        if (trimmed.equals("?")) return new ConsoleIntent.Utility(ConsoleIntent.UtilityCommand.HELP, "");

        if (!trimmed.isEmpty() && view != null && view.options() != null) {
            try {
                int index = Integer.parseInt(trimmed);
                if (index > 0 && index <= view.options().size() && view.options().get(index - 1).enabled()) {
                    return new ConsoleIntent.ChooseIndex(index);
                }
                return new ConsoleIntent.Invalid("CHOICE_NOT_AVAILABLE");
            } catch (NumberFormatException ignored) {
                var match = view.options().stream().filter(option -> option.enabled()
                        && option.id().equalsIgnoreCase(trimmed)).findFirst();
                if (match.isPresent()) return new ConsoleIntent.ChooseId(match.get().id());
            }
        }
        InputMode mode = view == null || view.input() == null ? InputMode.NONE : view.input().mode();
        if (trimmed.isEmpty()) {
            return mode == InputMode.NONE ? new ConsoleIntent.Advance()
                    : new ConsoleIntent.Invalid("ADVANCE_NOT_ALLOWED");
        }
        if (mode == InputMode.TEXT || mode == InputMode.CHOICE_OR_TEXT) {
            return new ConsoleIntent.SubmitText(line);
        }
        return new ConsoleIntent.Invalid(trimmed.equalsIgnoreCase("q")
                ? "QUIT_REQUIRES_SLASH" : "COMMAND_NOT_ALLOWED");
    }

    private ConsoleIntent utility(String input, boolean scripted, boolean developerMode) {
        int split = input.indexOf(' ');
        String name = (split < 0 ? input : input.substring(0, split)).toLowerCase(Locale.ROOT);
        String argument = split < 0 ? "" : input.substring(split + 1).trim();
        return switch (name) {
            case "/?", "/help" -> utility(ConsoleIntent.UtilityCommand.HELP, argument);
            case "/look" -> utility(ConsoleIntent.UtilityCommand.LOOK, argument);
            case "/status" -> utility(ConsoleIntent.UtilityCommand.STATUS, argument);
            case "/objectives", "/quests" -> utility(ConsoleIntent.UtilityCommand.OBJECTIVES, argument);
            case "/characters" -> utility(ConsoleIntent.UtilityCommand.CHARACTERS, argument);
            case "/map" -> utility(ConsoleIntent.UtilityCommand.MAP, argument);
            case "/save" -> utility(ConsoleIntent.UtilityCommand.SAVE, argument);
            case "/quit" -> utility(ConsoleIntent.UtilityCommand.QUIT, argument);
            case "/abandon" -> {
                if (scripted && !argument.equalsIgnoreCase("--yes")) yield new ConsoleIntent.Invalid("ABANDON_CONFIRMATION_REQUIRED");
                if (!argument.isEmpty() && !argument.equalsIgnoreCase("--yes")) yield new ConsoleIntent.Invalid("COMMAND_ARGUMENT_INVALID");
                yield utility(ConsoleIntent.UtilityCommand.ABANDON, argument);
            }
            case "/move" -> argument.isEmpty() ? new ConsoleIntent.Invalid("MOVE_REQUIRED") : new ConsoleIntent.Move(argument);
            case "/text" -> argument.isEmpty() ? new ConsoleIntent.Invalid("TEXT_REQUIRED") : new ConsoleIntent.SubmitText(argument);
            case "/events" -> developerMode ? utility(ConsoleIntent.UtilityCommand.EVENTS, argument) : new ConsoleIntent.Invalid("UNKNOWN_COMMAND");
            case "/debug" -> developerMode ? utility(ConsoleIntent.UtilityCommand.DEBUG, argument) : new ConsoleIntent.Invalid("UNKNOWN_COMMAND");
            case "/diagnostics" -> developerMode ? utility(ConsoleIntent.UtilityCommand.DIAGNOSTICS, argument) : new ConsoleIntent.Invalid("UNKNOWN_COMMAND");
            default -> new ConsoleIntent.Invalid("UNKNOWN_COMMAND");
        };
    }

    private ConsoleIntent utility(ConsoleIntent.UtilityCommand command, String argument) {
        return argument.isEmpty() || command == ConsoleIntent.UtilityCommand.DEBUG
                || command == ConsoleIntent.UtilityCommand.EVENTS
                || command == ConsoleIntent.UtilityCommand.STATUS
                ? new ConsoleIntent.Utility(command, argument)
                : new ConsoleIntent.Invalid("COMMAND_ARGUMENT_INVALID");
    }
}
