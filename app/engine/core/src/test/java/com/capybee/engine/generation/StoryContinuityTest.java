package com.capybee.engine.generation;

import com.capybee.engine.generation.GenerationArtifact.*;
import com.capybee.engine.pack.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class StoryContinuityTest {
    @Test void selectsVisitedFactsByPhasePriorityAndReturnsChronologicalOrder() {
        Map<String, GenerationArtifact> scenes = new LinkedHashMap<>();
        scenes.put("old", artifact("old", List.of("old fact", "duplicate fact")));
        scenes.put("previous", artifact("previous", List.of("previous fact", "duplicate fact")));
        scenes.put("current", artifact("current", List.of("current fact")));
        scenes.put("unvisited", artifact("unvisited", List.of("never sent")));
        Map<String, String> phases = Map.of("old", "opening", "previous", "development",
                "current", "challenge", "unvisited", "challenge", "destination", "challenge");

        var selected = StoryContinuity.selectContinuity(List.of("old", "previous", "current"), scenes,
                phases, "destination", "challenge", 3, 100);

        assertThat(selected).extracting(StoryContinuity.ContinuityFact::text)
                .containsExactly("old fact", "previous fact", "current fact");
    }

    @Test void excludesDestinationAndHonorsCodePointBudget() {
        Map<String, GenerationArtifact> scenes = Map.of("same", artifact("same", List.of("self")),
                "prior", artifact("prior", List.of("😀😀", "later")));
        var selected = StoryContinuity.selectContinuity(List.of("prior", "same"), scenes,
                Map.of("same", "ending", "prior", "resolution"), "same", "ending", 12, 2);
        assertThat(selected).extracting(StoryContinuity.ContinuityFact::text).containsExactly("😀😀");
    }

    private GenerationArtifact artifact(String sceneId, List<String> facts) {
        return new GenerationArtifact(2, new PackId("fixture-generated-full-minimal"),
                new PackVersion(1, 0, 0), new LocaleTag("en"), GenerationCatalog.Stage.SCENE,
                "scene-basic", sceneId + "-artifact", Outcome.GENERATED,
                new PlannedScenePayload(sceneId, "Title", "Narration", List.of(), List.of(), facts));
    }
}
