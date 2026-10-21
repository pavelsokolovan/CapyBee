package com.capybee.engine.api;

import com.capybee.engine.content.Block;

import java.util.List;

public record View(List<Block> blocks, List<Option> options, InputSpec input, List<StatusItem> status,
                   boolean ended, EndReason endReason, List<ObjectiveItem> objectives, String outcome,
                   PlacesView places, StoryInfo story, List<CharacterItem> characters) {
    public static final int CURRENT_VIEW_SCHEMA = 2;

    public View {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        options = options == null ? List.of() : List.copyOf(options);
        status = status == null ? List.of() : List.copyOf(status);
        objectives = objectives == null ? List.of() : List.copyOf(objectives);
        characters = characters == null ? List.of() : List.copyOf(characters);
    }

    public View(List<Block> blocks, List<Option> options, InputSpec input, List<StatusItem> status,
                boolean ended, EndReason endReason) {
        this(blocks, options, input, status, ended, endReason, List.of(), null, null, null, List.of());
    }

    public View(List<Block> blocks, List<Option> options, InputSpec input, List<StatusItem> status,
                boolean ended, EndReason endReason, List<ObjectiveItem> objectives, String outcome) {
        this(blocks, options, input, status, ended, endReason, objectives, outcome, null, null, List.of());
    }

    public View(List<Block> blocks, List<Option> options, InputSpec input, List<StatusItem> status,
                boolean ended, EndReason endReason, List<ObjectiveItem> objectives, String outcome,
                PlacesView places) {
        this(blocks, options, input, status, ended, endReason, objectives, outcome, places, null, List.of());
    }
}
