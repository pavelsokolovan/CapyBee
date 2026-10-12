package com.capybee.engine.api;

import com.capybee.engine.content.Block;

import java.util.List;

public record View(List<Block> blocks, List<Option> options, InputSpec input, List<StatusItem> status,
                   boolean ended, EndReason endReason) {
    public View {
        blocks = List.copyOf(blocks);
        options = List.copyOf(options);
        status = List.copyOf(status);
    }
}
