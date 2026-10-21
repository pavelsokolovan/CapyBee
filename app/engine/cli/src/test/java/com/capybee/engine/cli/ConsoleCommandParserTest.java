package com.capybee.engine.cli;

import com.capybee.engine.api.InputMode;
import com.capybee.engine.api.InputSpec;
import com.capybee.engine.api.Option;
import com.capybee.engine.api.View;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConsoleCommandParserTest {
    private final ConsoleCommandParser parser = new ConsoleCommandParser();

    @Test
    void parsesTrustedChoicesAndNeverTreatsBareQAsQuit() {
        View view = view(InputMode.CHOICE, List.of(new Option("q", "Question", true, null),
                new Option("locked", "Locked", false, null)));
        assertThat(parser.parse("1", view, false, false)).isEqualTo(new ConsoleIntent.ChooseIndex(1));
        assertThat(parser.parse("q", view, false, false)).isEqualTo(new ConsoleIntent.ChooseId("q"));
        assertThat(parser.parse("2", view, false, false)).isEqualTo(new ConsoleIntent.Invalid("CHOICE_NOT_AVAILABLE"));
        assertThat(parser.parse("q", view(InputMode.CHOICE, List.of()), false, false))
                .isEqualTo(new ConsoleIntent.Invalid("QUIT_REQUIRES_SLASH"));
    }

    @Test
    void reservesSlashCommandsAndGatesDeveloperCommands() {
        View text = view(InputMode.TEXT, List.of());
        assertThat(parser.parse("/text /look", text, false, false)).isEqualTo(new ConsoleIntent.SubmitText("/look"));
        assertThat(parser.parse("/unknown secret", text, false, false)).isEqualTo(new ConsoleIntent.Invalid("UNKNOWN_COMMAND"));
        assertThat(parser.parse("/events", text, false, false)).isEqualTo(new ConsoleIntent.Invalid("UNKNOWN_COMMAND"));
        assertThat(parser.parse("/events", text, false, true))
                .isEqualTo(new ConsoleIntent.Utility(ConsoleIntent.UtilityCommand.EVENTS, ""));
        assertThat(parser.parse("/abandon", text, true, false))
                .isEqualTo(new ConsoleIntent.Invalid("ABANDON_CONFIRMATION_REQUIRED"));
    }

    @Test
    void resolvesPlatformStateDirectories() {
        assertThat(ConsolePaths.resolve(Map.of("LOCALAPPDATA", "C:\\State"), "Windows 11", Path.of("home")))
                .isEqualTo(Path.of("C:\\State", "CapyBee", "cli"));
        assertThat(ConsolePaths.resolve(Map.of("XDG_STATE_HOME", "/state"), "Linux", Path.of("/home/player")))
                .isEqualTo(Path.of("/state", "capybee", "cli"));
        assertThat(ConsolePaths.resolve(Map.of(), "Mac OS X", Path.of("/Users/player")))
                .isEqualTo(Path.of("/Users/player/Library/Application Support/CapyBee/cli"));
    }

    private View view(InputMode mode, List<Option> options) {
        return new View(List.of(), options, new InputSpec(mode, 100), List.of(), false, null);
    }
}
