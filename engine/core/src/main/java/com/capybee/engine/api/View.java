package com.capybee.engine.api;

import com.capybee.engine.content.Block;

import java.util.List;

public record View(List<Block> blocks, List<Option> options, InputSpec input, List<StatusItem> status,
                   boolean ended, EndReason endReason, List<ObjectiveItem> objectives, String outcome) {
    public View {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        options = options == null ? List.of() : List.copyOf(options);
        status = status == null ? List.of() : List.copyOf(status);
        objectives = objectives == null ? List.of() : List.copyOf(objectives);
    }

    public View(List<Block> blocks, List<Option> options, InputSpec input, List<StatusItem> status,
                boolean ended, EndReason endReason) {
        this(blocks, options, input, status, ended, endReason, List.of(), null);
    }
}
