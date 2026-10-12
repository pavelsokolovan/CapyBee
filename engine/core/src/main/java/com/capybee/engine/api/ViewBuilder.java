package com.capybee.engine.api;

import com.capybee.engine.content.Scene;
import com.capybee.engine.content.SceneOption;

import java.util.List;

final class ViewBuilder {
    private ViewBuilder() {}

    static View build(Scene scene, boolean ended, EndReason endReason) {
        List<Option> options = scene == null || scene.options() == null ? List.of()
                : scene.options().stream().map(ViewBuilder::toOption).toList();
        return build(scene, ended, endReason, options, List.of(), List.of(), null);
    }

    static View build(Scene scene, boolean ended, EndReason endReason, List<Option> options,
                      List<StatusItem> status, List<ObjectiveItem> objectives, String outcome) {
        if (scene == null) {
            return new View(List.of(), List.of(), new InputSpec(InputMode.NONE, 0), List.of(), true, endReason);
        }

        InputMode mode = InputMode.NONE;
        if (!ended && !options.isEmpty()) {
            mode = InputMode.CHOICE;
        }

        if (ended && endReason == EndReason.ABANDONED) {
            return new View(List.of(), List.of(), new InputSpec(InputMode.NONE, 0), List.of(), true, endReason);
        }

        return new View(List.copyOf(scene.blocks()), ended ? List.of() : options,
            new InputSpec(mode, 0), status, ended, endReason, objectives, outcome);
    }

    private static Option toOption(SceneOption option) {
        return new Option(option.id(), option.label(), true, null);
    }
}
