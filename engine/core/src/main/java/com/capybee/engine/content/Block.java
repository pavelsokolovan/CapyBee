package com.capybee.engine.content;

import java.util.List;

public record Block(BlockKind kind, String speaker, String text, List<String> refs) {
    public Block {
        refs = refs == null ? List.of() : List.copyOf(refs);
    }
}
