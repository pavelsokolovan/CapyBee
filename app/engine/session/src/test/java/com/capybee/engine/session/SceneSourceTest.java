package com.capybee.engine.session;

import com.capybee.engine.api.PackRef;
import com.capybee.engine.api.RunSnapshot;
import com.capybee.engine.api.RunState;
import com.capybee.engine.generation.GenerationArtifact;
import com.capybee.engine.generation.GenerationCatalog;
import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.PackId;
import com.capybee.engine.pack.PackVersion;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.capybee.engine.session.SessionTypes.*;
import static org.assertj.core.api.Assertions.assertThat;

class SceneSourceTest {
    @Test
    void classifiesEverySceneSourceBranch() {
        RunSnapshot authored = snapshot("next", List.of());
        assertThat(RunSessionService.sceneSource("start", snapshot("start", List.of(scene("start",
                GenerationArtifact.Outcome.GENERATED))), List.of(), false)).isEqualTo(SceneSource.NONE);
        assertThat(RunSessionService.sceneSource("start", authored, List.of(), false)).isEqualTo(SceneSource.NONE);
        assertThat(RunSessionService.sceneSource("start", snapshot("next", List.of(scene("next",
                GenerationArtifact.Outcome.GENERATED))),
                List.of(new PrefetchedArtifact("next", 0, scene("next", GenerationArtifact.Outcome.GENERATED))),
                false)).isEqualTo(SceneSource.PREFETCHED);
        assertThat(source(GenerationArtifact.Outcome.FALLBACK, false)).isEqualTo(SceneSource.FALLBACK);
        assertThat(source(GenerationArtifact.Outcome.REPAIRED, false)).isEqualTo(SceneSource.INLINE_REPAIRED);
        assertThat(source(GenerationArtifact.Outcome.GENERATED, true)).isEqualTo(SceneSource.JOINED_PREFETCH);
        assertThat(source(GenerationArtifact.Outcome.GENERATED, false)).isEqualTo(SceneSource.INLINE_GENERATED);
    }

    private static SceneSource source(GenerationArtifact.Outcome outcome, boolean joined) {
        return RunSessionService.sceneSource("start", snapshot("next", List.of(scene("next", outcome))),
                List.of(), joined);
    }

    private static RunSnapshot snapshot(String sceneId, List<GenerationArtifact> artifacts) {
        Instant now = Instant.parse("2026-10-19T10:00:00Z");
        return new RunSnapshot(1, "run-id", new PackRef(new PackId("test-pack"), new PackVersion(1, 0, 0)),
                new LocaleTag("en"), 1, Map.of(), now, now, 1,
                new RunState(sceneId, List.of("start", sceneId), false, null, Map.of(), Map.of(), Map.of(),
                        Map.of(), null, null, List.of(), artifacts));
    }

    private static GenerationArtifact scene(String sceneId, GenerationArtifact.Outcome outcome) {
        return new GenerationArtifact(2, new PackId("test-pack"),
                new PackVersion(1, 0, 0), new LocaleTag("en"), GenerationCatalog.Stage.SCENE, "scene-template",
                "artifact-" + sceneId, outcome,
                new GenerationArtifact.ScenePayload(sceneId, "Title", "Narration", List.of(), List.of()));
    }
}
