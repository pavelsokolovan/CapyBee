package com.capybee.engine.content;

import java.util.List;

public record Scene(String id, boolean start, List<Block> blocks, List<SceneOption> options,
                   String next, boolean end) {
    public Scene {
        blocks = List.copyOf(blocks);
        options = List.copyOf(options);
    }
}
