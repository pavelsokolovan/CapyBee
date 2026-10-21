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
        return build(scene, ended, endReason, options, status, objectives, outcome, null);
    }

    static View build(Scene scene, boolean ended, EndReason endReason, List<Option> options,
                      List<StatusItem> status, List<ObjectiveItem> objectives, String outcome, PlacesView places) {
        return build(scene, ended, endReason, options, status, objectives, outcome, places, null, List.of());
    }

    static View build(Scene scene, boolean ended, EndReason endReason, List<Option> options,
                      List<StatusItem> status, List<ObjectiveItem> objectives, String outcome, PlacesView places,
                      StoryInfo story, List<CharacterItem> characters) {
        if (scene == null) {
            return new View(List.of(), List.of(), new InputSpec(InputMode.NONE, 0), List.of(), true,
                    endReason, objectives, outcome, places, story, characters);
        }

        InputMode mode = ended ? InputMode.NONE : scene.inputMode() != null ? InputMode.valueOf(scene.inputMode().name())
            : !options.isEmpty() ? InputMode.CHOICE : InputMode.NONE;

        if (ended && endReason == EndReason.ABANDONED) {
            return new View(List.of(), List.of(), new InputSpec(InputMode.NONE, 0), List.of(), true,
                    endReason, objectives, outcome, places, story, characters);
        }

        return new View(List.copyOf(scene.blocks()), ended ? List.of() : options,
            new InputSpec(mode, ended ? 0 : scene.maxChars()), status, ended, endReason, objectives, outcome, places,
            story, characters);
    }

    private static Option toOption(SceneOption option) {
        return new Option(option.id(), option.label(), true, null);
    }
}
