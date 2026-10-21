package com.capybee.engine.generation;
import java.util.List;
public record CompletionContract(List<EndingContract> endings) {
    public CompletionContract { endings = List.copyOf(endings); }
}
