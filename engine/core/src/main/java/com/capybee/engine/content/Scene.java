package com.capybee.engine.content;

import com.capybee.engine.rules.Effect;

import java.util.List;

public record Scene(String id, boolean start, List<Block> blocks, List<SceneOption> options,
                   String next, boolean end, List<Effect> onEnter, List<SceneBranch> branches,
                   String outcome, SceneInputMode inputMode, int maxChars) {
    public Scene {
        blocks = List.copyOf(blocks);
        options = List.copyOf(options);
        onEnter = onEnter == null ? List.of() : List.copyOf(onEnter);
        branches = branches == null ? List.of() : List.copyOf(branches);
    }

    public Scene(String id, boolean start, List<Block> blocks, List<SceneOption> options,
                 String next, boolean end) {
        this(id, start, blocks, options, next, end, List.of(), List.of(), null, null, 0);
    }

    public Scene(String id, boolean start, List<Block> blocks, List<SceneOption> options,
                 String next, boolean end, List<Effect> onEnter, List<SceneBranch> branches, String outcome) {
        this(id, start, blocks, options, next, end, onEnter, branches, outcome, null, 0);
    }
}
