package com.capybee.engine.api;

import com.capybee.engine.content.Scene;
import com.capybee.engine.content.SceneOption;

import java.util.List;

final class ViewBuilder {
    private ViewBuilder() {}

    static View build(Scene scene, boolean ended, EndReason endReason) {
        if (scene == null) {
            return new View(List.of(), List.of(), new InputSpec(InputMode.NONE, 0), List.of(), true, endReason);
        }

        List<Option> options = List.of();
        InputMode mode = InputMode.NONE;
        if (!ended && scene.options() != null && !scene.options().isEmpty()) {
            options = scene.options().stream()
                    .map(ViewBuilder::toOption)
                    .toList();
            mode = InputMode.CHOICE;
        }

        if (ended && endReason == EndReason.ABANDONED) {
            return new View(List.of(), List.of(), new InputSpec(InputMode.NONE, 0), List.of(), true, endReason);
        }

        return new View(List.copyOf(scene.blocks()), options,
                new InputSpec(mode, 0), List.of(), ended, endReason);
    }

    private static Option toOption(SceneOption option) {
        return new Option(option.id(), option.label(), true, null);
    }
}
